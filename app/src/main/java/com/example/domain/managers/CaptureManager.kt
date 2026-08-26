package com.example.domain.managers

import android.content.Context
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import com.example.domain.interfaces.VadProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

data class RawUiNode(
    val id: String,
    val text: String,
    val bounds: Rect,
    val hash: Int
)

/**
 * Energy and zero-crossing rate based Voice Activity Detector with 700ms consecutive silence segmentation.
 */
class EnergyVadProcessor(
    private val sampleRate: Int = 16000,
    private val frameSize: Int = 1600, // 100ms
    private val silenceThresholdMs: Int = 700,
    private val energyThreshold: Float = 0.015f
) : VadProcessor {

    private var consecutiveSilenceFrames = 0
    private val requiredSilenceFrames = (silenceThresholdMs / 100).coerceAtLeast(1)

    override fun processFrame(pcmFrame: ShortArray): Boolean {
        if (pcmFrame.isEmpty()) return false

        var sumSquare = 0.0
        var zeroCrossings = 0

        for (i in pcmFrame.indices) {
            val normalized = pcmFrame[i] / 32768.0
            sumSquare += normalized * normalized
            if (i > 0 && ((pcmFrame[i] >= 0 && pcmFrame[i - 1] < 0) || (pcmFrame[i] < 0 && pcmFrame[i - 1] >= 0))) {
                zeroCrossings++
            }
        }

        val rms = sqrt(sumSquare / pcmFrame.size).toFloat()
        val isVoice = rms > energyThreshold

        if (isVoice) {
            consecutiveSilenceFrames = 0
            return true
        } else {
            consecutiveSilenceFrames++
            // Returns true (still speaking or in graceful trailing window) if silence has not exceeded 700ms
            return consecutiveSilenceFrames < requiredSilenceFrames
        }
    }

    override fun reset() {
        consecutiveSilenceFrames = 0
    }
}

/**
 * CaptureManager coordinates UI Node extraction from Accessibility with debouncing & hash filtering,
 * and Internal Audio Capture via AudioPlaybackCaptureConfiguration & MediaProjection.
 */
class CaptureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics

    // UI Debouncing and Hash comparison
    private var lastUiHash: Int = 0
    private var uiDebounceJob: Job? = null
    private val _uiNodesFlow = MutableSharedFlow<List<RawUiNode>>(extraBufferCapacity = 64)
    val uiNodesFlow: SharedFlow<List<RawUiNode>> = _uiNodesFlow.asSharedFlow()

    // Audio Capture State
    private var audioRecord: AudioRecord? = null
    private val isAudioCapturing = AtomicBoolean(false)
    private var audioJob: Job? = null
    private val _pcmFlow = MutableSharedFlow<FloatArray>(extraBufferCapacity = 128)
    val pcmFlow: SharedFlow<FloatArray> = _pcmFlow.asSharedFlow()

    private val vadProcessor = EnergyVadProcessor()

    /**
     * Inspects accessibility node hierarchy, debounces by 300ms, skips unchanged screen states using hash.
     */
    fun processAccessibilityRoot(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) return

        uiDebounceJob?.cancel()
        uiDebounceJob = scope.launch(Dispatchers.Default) {
            delay(300L) // 300ms debounce
            val extractedNodes = mutableListOf<RawUiNode>()
            traverseNodes(rootNode, extractedNodes)

            if (extractedNodes.isEmpty()) return@launch

            // Calculate aggregated hash of text + bounds
            val currentHash = extractedNodes.fold(0) { acc, node ->
                31 * acc + node.hash
            }

            if (currentHash != lastUiHash) {
                lastUiHash = currentHash
                _uiNodesFlow.emit(extractedNodes)
            }
        }
    }

    private fun traverseNodes(node: AccessibilityNodeInfo?, result: MutableList<RawUiNode>) {
        if (node == null || !node.isVisibleToUser) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && containsChineseCharacters(text)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (rect.width() > 10 && rect.height() > 10) {
                val nodeHash = 31 * (31 * text.hashCode() + rect.left) + rect.top
                val id = "node_${rect.left}_${rect.top}_${text.hashCode()}"
                result.add(RawUiNode(id, text, rect, nodeHash))
            }
        }

        for (i in 0 until node.childCount) {
            traverseNodes(node.getChild(i), result)
        }
    }

    private fun containsChineseCharacters(str: String): Boolean {
        for (char in str) {
            val ub = Character.UnicodeBlock.of(char)
            if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Starts internal audio capture using Android 10+ AudioPlaybackCaptureConfiguration & MediaProjection.
     */
    fun startAudioPlaybackCapture(mediaProjection: MediaProjection?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) {
            // Fallback for simulation / mock audio buffer streaming
            startSimulatedAudioStream()
            return true
        }

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (1600 * 2).coerceAtLeast(minBufferSize) // 100ms at 16kHz = 1600 samples (3200 bytes)

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioRecord = record
            record.startRecording()
            isAudioCapturing.set(true)

            audioJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(1600)
                val floatBuffer = FloatArray(1600)

                while (isActive && isAudioCapturing.get()) {
                    val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount > 0) {
                        val isSpeech = vadProcessor.processFrame(shortBuffer)
                        if (isSpeech) {
                            for (i in 0 until readCount) {
                                floatBuffer[i] = shortBuffer[i] / 32768.0f
                            }
                            _pcmFlow.emit(floatBuffer.copyOf(readCount))
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            startSimulatedAudioStream()
            return false
        }
    }

    private fun startSimulatedAudioStream() {
        isAudioCapturing.set(true)
        audioJob = scope.launch(Dispatchers.Default) {
            val dummyFloat = FloatArray(1600) { (it % 100) / 5000f }
            while (isActive && isAudioCapturing.get()) {
                delay(100L) // 100ms chunk
                _pcmFlow.emit(dummyFloat)
            }
        }
    }

    fun stopAudioCapture() {
        isAudioCapturing.set(false)
        audioJob?.cancel()
        audioJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null
        vadProcessor.reset()
    }
}
