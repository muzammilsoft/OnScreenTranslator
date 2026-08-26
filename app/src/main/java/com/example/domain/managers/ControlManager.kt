package com.example.domain.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.projection.MediaProjection
import com.example.data.local.cache.AppDatabase
import com.example.data.local.prefs.AppSettings
import com.example.data.local.prefs.SettingsRepository
import com.example.data.model.ModelDownloadManager
import com.example.domain.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ControlManager is the primary orchestrator uniting all 5 managers and services.
 */
class ControlManager(
    private val context: Context,
    private val appScope: CoroutineScope,
    val settingsRepository: SettingsRepository,
    val database: AppDatabase,
    val modelDownloadManager: ModelDownloadManager
) {
    // 5 Core Managers
    val captureManager = CaptureManager(context, appScope)
    val recognitionManager = RecognitionManager(appScope)
    val translationManager = TranslationManager(database)
    val overlayManager = OverlayManager(context, appScope)

    // State Machine
    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val state: StateFlow<TranslationState> = _state.asStateFlow()

    // Subtitle stream state
    private val _currentSubtitles = MutableStateFlow<List<SubtitleItem>>(emptyList())
    val currentSubtitles: StateFlow<List<SubtitleItem>> = _currentSubtitles.asStateFlow()

    // Active UI Nodes
    private val _activeUiNodes = MutableStateFlow<List<TranslatedNode>>(emptyList())
    val activeUiNodes: StateFlow<List<TranslatedNode>> = _activeUiNodes.asStateFlow()

    // Service active switch
    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private var currentSettings = AppSettings()
    private var isBilibiliForeground = false
    private var screenOffReceiver: BroadcastReceiver? = null

    init {
        registerScreenStateReceiver()
        observeSettings()
        observeUiCaptureStream()
        observeAudioStream()
    }

    private fun observeSettings() {
        appScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
                overlayManager.updateSettings(settings)
                reinitializeProviders(settings)
            }
        }
    }

    fun reinitializeProviders(settings: AppSettings = currentSettings) {
        recognitionManager.updateOcrEngine(settings.ocrEngine)
        recognitionManager.updateSttEngine(settings.sttEngine)
        translationManager.updateSettings(
            settings.translationEngine,
            settings.targetLanguage,
            settings.isOfflineOnly
        )
    }

    private fun observeUiCaptureStream() {
        appScope.launch {
            captureManager.uiNodesFlow.collect { rawNodes ->
                if (!_isServiceActive.value) return@collect

                _state.value = TranslationState.Processing("Translating UI Nodes", 0.5f)
                val textsToTranslate = rawNodes.map { it.text }
                val translatedTexts = translationManager.translateBatch(textsToTranslate, "zh")

                val translatedNodes = mutableListOf<TranslatedNode>()
                for (i in rawNodes.indices) {
                    val raw = rawNodes[i]
                    val trans = if (i < translatedTexts.size) translatedTexts[i] else raw.text
                    translatedNodes.add(
                        TranslatedNode(
                            id = raw.id,
                            sourceText = raw.text,
                            translatedText = trans,
                            screenBounds = raw.bounds,
                            isRtl = currentSettings.targetLanguage.isRtl
                        )
                    )
                }

                _activeUiNodes.value = translatedNodes
                overlayManager.renderUiNodes(translatedNodes)
                _state.value = TranslationState.Displaying(
                    activeUiBadges = translatedNodes.size,
                    subtitleCount = _currentSubtitles.value.size
                )
            }
        }
    }

    private fun observeAudioStream() {
        appScope.launch {
            recognitionManager.streamAudioTranscription(captureManager.pcmFlow).collect { sttResult ->
                if (!_isServiceActive.value) return@collect

                if (sttResult.isFinal) {
                    _state.value = TranslationState.Processing("Translating Speech", 0.8f)
                    val translated = translationManager.translateText(sttResult.text, "zh")
                    val subtitle = SubtitleItem(
                        id = "sub_${System.currentTimeMillis()}",
                        originalText = sttResult.text,
                        translatedText = translated,
                        isInterim = false,
                        targetLang = currentSettings.targetLanguage.code
                    )

                    val updated = _currentSubtitles.value.toMutableList().apply {
                        add(subtitle)
                        if (size > 5) removeAt(0)
                    }
                    _currentSubtitles.value = updated
                    overlayManager.updateSubtitle(subtitle)
                } else {
                    // Interim subtitle update
                    val interimSub = SubtitleItem(
                        id = "interim_active",
                        originalText = sttResult.text,
                        translatedText = sttResult.text,
                        isInterim = true,
                        targetLang = currentSettings.targetLanguage.code
                    )
                    overlayManager.updateSubtitle(interimSub)
                }

                _state.value = TranslationState.CapturingAudio(
                    isSpeaking = !sttResult.isFinal,
                    audioLevel = if (sttResult.isFinal) 0.1f else 0.8f
                )
            }
        }
    }

    /**
     * Toggles translation engine state ON/OFF within 500ms.
     */
    fun toggleService(mediaProjection: MediaProjection? = null): Boolean {
        return if (_isServiceActive.value) {
            stopTranslationService()
            false
        } else {
            startTranslationService(mediaProjection)
            true
        }
    }

    fun startTranslationService(mediaProjection: MediaProjection? = null) {
        _isServiceActive.value = true
        _state.value = TranslationState.CapturingUi()
        overlayManager.showUiOverlay()
        captureManager.startAudioPlaybackCapture(mediaProjection)
    }

    fun stopTranslationService() {
        _isServiceActive.value = false
        _state.value = TranslationState.Idle
        _activeUiNodes.value = emptyList()
        _currentSubtitles.value = emptyList()
        captureManager.stopAudioCapture()
        overlayManager.clearAllOverlays()
    }

    fun notifyPackageChanged(packageName: String?) {
        val isBili = packageName?.contains("bili") == true || packageName == "com.example"
        isBilibiliForeground = isBili

        if (!isBili && currentSettings.autoStartOnBilibili && _isServiceActive.value) {
            // Auto pause if Bilibili is not in foreground
            overlayManager.clearAllOverlays()
        }
    }

    private fun registerScreenStateReceiver() {
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    // Pause captures when screen turns off to save battery
                    if (_isServiceActive.value) {
                        captureManager.stopAudioCapture()
                        overlayManager.clearAllOverlays()
                    }
                } else if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    if (_isServiceActive.value) {
                        captureManager.startAudioPlaybackCapture(null)
                        overlayManager.showUiOverlay()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(screenOffReceiver, filter)
    }

    fun cleanup() {
        stopTranslationService()
        screenOffReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (ignored: Exception) {}
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ControlManager? = null

        fun getInstance(context: Context): ControlManager {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val settingsRepo = SettingsRepository(context)
                val modelManager = ModelDownloadManager(context)
                val instance = ControlManager(context.applicationContext, scope, settingsRepo, db, modelManager)
                INSTANCE = instance
                instance
            }
        }
    }
}
