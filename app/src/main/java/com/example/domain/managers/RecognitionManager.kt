package com.example.domain.managers

import android.graphics.Bitmap
import com.example.data.providers.ocr.MlKitOcrProvider
import com.example.data.providers.ocr.PaddleOcrProvider
import com.example.data.providers.stt.IFlytekProvider
import com.example.data.providers.stt.SherpaOnnxProvider
import com.example.domain.interfaces.OcrProvider
import com.example.domain.interfaces.OcrResult
import com.example.domain.interfaces.SttProvider
import com.example.domain.interfaces.SttResult
import com.example.domain.state.OcrEngine
import com.example.domain.state.SttEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * RecognitionManager coordinates OCR visual text recognition and STT audio streaming.
 */
class RecognitionManager(
    private val scope: CoroutineScope
) {
    private var currentOcrProvider: OcrProvider = MlKitOcrProvider()
    private var currentSttProvider: SttProvider = SherpaOnnxProvider()

    fun updateOcrEngine(engine: OcrEngine) {
        currentOcrProvider = when (engine) {
            OcrEngine.MLKIT_CHINESE -> MlKitOcrProvider()
            OcrEngine.PADDLE_OCR -> PaddleOcrProvider()
        }
    }

    fun updateSttEngine(engine: SttEngine) {
        currentSttProvider = when (engine) {
            SttEngine.SHERPA_ONNX -> SherpaOnnxProvider()
            SttEngine.IFLYTEK_STREAM -> IFlytekProvider()
        }
    }

    /**
     * Executes OCR on a captured bitmap and immediately guarantees bitmap recycling.
     */
    suspend fun recognizeScreenBitmap(bitmap: Bitmap, autoRecycle: Boolean = true): Result<OcrResult> = withContext(Dispatchers.Default) {
        try {
            val result = currentOcrProvider.recognizeText(bitmap)
            if (result.isFailure && currentOcrProvider is MlKitOcrProvider) {
                // Fallback to PaddleOCR backup provider
                val backup = PaddleOcrProvider()
                backup.recognizeText(bitmap)
            } else {
                result
            }
        } finally {
            if (autoRecycle && !bitmap.isRecycled) {
                bitmap.recycle() // Recycle immediately to avoid memory leaks
            }
        }
    }

    /**
     * Connects PCM audio stream to current STT provider.
     */
    fun streamAudioTranscription(pcmFlow: Flow<FloatArray>): Flow<SttResult> {
        val flow = currentSttProvider.streamAudio(pcmFlow)
        return if (currentSttProvider.isOffline) {
            flow.flowOn(Dispatchers.Default) // CPU intensive
        } else {
            flow.flowOn(Dispatchers.IO) // Network I/O
        }
    }

    suspend fun stopSttStream() {
        currentSttProvider.stopStream()
    }
}
