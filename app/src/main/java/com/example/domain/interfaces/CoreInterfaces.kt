package com.example.domain.interfaces

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.flow.Flow

data class OcrRecognizedBlock(
    val text: String,
    val bounds: Rect,
    val confidence: Float
)

data class OcrResult(
    val blocks: List<OcrRecognizedBlock>,
    val fullText: String,
    val processTimeMs: Long
)

interface OcrProvider {
    val providerName: String
    val isOffline: Boolean
    suspend fun recognizeText(bitmap: Bitmap): Result<OcrResult>
}

data class SttResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 0.9f,
    val latencyMs: Long = 0
)

interface SttProvider {
    val providerName: String
    val isOffline: Boolean
    fun streamAudio(pcmFlow: Flow<FloatArray>): Flow<SttResult>
    suspend fun stopStream()
}

interface Translator {
    val providerName: String
    val isOffline: Boolean
    suspend fun translate(
        text: String,
        sourceLang: String = "zh",
        targetLang: String = "ar"
    ): Result<String>

    suspend fun translateBatch(
        texts: List<String>,
        sourceLang: String = "zh",
        targetLang: String = "ar"
    ): Result<List<String>>
}

interface VadProcessor {
    fun processFrame(pcmFrame: ShortArray): Boolean
    fun reset()
}
