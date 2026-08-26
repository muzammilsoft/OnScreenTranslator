package com.example.domain.state

import android.graphics.Rect

/**
 * Finite State Machine states for the translation pipeline.
 */
sealed class TranslationState {
    object Idle : TranslationState()
    data class CapturingUi(val detectedNodesCount: Int = 0) : TranslationState()
    data class CapturingAudio(val isSpeaking: Boolean = false, val audioLevel: Float = 0f) : TranslationState()
    data class Processing(val task: String, val progress: Float = 0f) : TranslationState()
    data class Displaying(val activeUiBadges: Int = 0, val subtitleCount: Int = 0) : TranslationState()
    data class Error(val message: String, val retryCount: Int = 0, val recoverable: Boolean = true) : TranslationState()
}

/**
 * Model representing a detected and translated UI element node.
 */
data class TranslatedNode(
    val id: String,
    val sourceText: String,
    val translatedText: String,
    val screenBounds: Rect,
    val confidence: Float = 0.95f,
    val isRtl: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Model representing a real-time speech-to-text subtitle line.
 */
data class SubtitleItem(
    val id: String,
    val originalText: String,
    val translatedText: String,
    val isInterim: Boolean = false,
    val targetLang: String = "ar",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Provider choices for OCR, STT, and Translation engines.
 */
enum class OcrEngine(val displayName: String, val isOffline: Boolean) {
    MLKIT_CHINESE("ML Kit Vision (Primary Offline)", true),
    PADDLE_OCR("PaddleOCR Mobile (Backup Offline)", true)
}

enum class SttEngine(val displayName: String, val isOffline: Boolean) {
    SHERPA_ONNX("Sherpa-ONNX Paraformer (Offline)", true),
    IFLYTEK_STREAM("iFlytek Cloud Stream (Online)", false)
}

enum class TranslationEngine(val displayName: String, val isOffline: Boolean) {
    MLKIT_TRANSLATE("ML Kit On-Device (Direct/Chained)", true),
    NLLB_TFLITE("NLLB Neural Engine (Offline)", true),
    BAIDU_ONLINE("Baidu AI Translate (Online)", false)
}

enum class TargetLanguage(val code: String, val label: String, val isRtl: Boolean) {
    ARABIC("ar", "العربية (Arabic)", true),
    ENGLISH("en", "English", false)
}
