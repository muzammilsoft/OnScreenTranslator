package com.example.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.state.OcrEngine
import com.example.domain.state.SttEngine
import com.example.domain.state.TargetLanguage
import com.example.domain.state.TranslationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "translator_settings")

data class AppSettings(
    val ocrEngine: OcrEngine = OcrEngine.MLKIT_CHINESE,
    val sttEngine: SttEngine = SttEngine.SHERPA_ONNX,
    val translationEngine: TranslationEngine = TranslationEngine.MLKIT_TRANSLATE,
    val targetLanguage: TargetLanguage = TargetLanguage.ARABIC,
    val isOfflineOnly: Boolean = false,
    val overlayOpacity: Float = 0.85f,
    val fontSizeSp: Int = 16,
    val easternArabicNumerals: Boolean = false,
    val autoStartOnBilibili: Boolean = true,
    val showOriginalUnderlay: Boolean = true,
    val subtitleLinesCount: Int = 3,
    val vadSilenceThresholdMs: Int = 700,
    val uiDebounceMs: Long = 300L
)

class SettingsRepository(private val context: Context) {

    private object PreferenceKeys {
        val OCR_ENGINE = stringPreferencesKey("ocr_engine")
        val STT_ENGINE = stringPreferencesKey("stt_engine")
        val TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val IS_OFFLINE_ONLY = booleanPreferencesKey("is_offline_only")
        val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
        val EASTERN_ARABIC_NUMERALS = booleanPreferencesKey("eastern_arabic_numerals")
        val AUTO_START_BILIBILI = booleanPreferencesKey("auto_start_bilibili")
        val SHOW_ORIGINAL_UNDERLAY = booleanPreferencesKey("show_original_underlay")
        val SUBTITLE_LINES = intPreferencesKey("subtitle_lines")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            ocrEngine = runCatching { OcrEngine.valueOf(preferences[PreferenceKeys.OCR_ENGINE] ?: OcrEngine.MLKIT_CHINESE.name) }.getOrDefault(OcrEngine.MLKIT_CHINESE),
            sttEngine = runCatching { SttEngine.valueOf(preferences[PreferenceKeys.STT_ENGINE] ?: SttEngine.SHERPA_ONNX.name) }.getOrDefault(SttEngine.SHERPA_ONNX),
            translationEngine = runCatching { TranslationEngine.valueOf(preferences[PreferenceKeys.TRANSLATION_ENGINE] ?: TranslationEngine.MLKIT_TRANSLATE.name) }.getOrDefault(TranslationEngine.MLKIT_TRANSLATE),
            targetLanguage = runCatching { TargetLanguage.valueOf(preferences[PreferenceKeys.TARGET_LANGUAGE] ?: TargetLanguage.ARABIC.name) }.getOrDefault(TargetLanguage.ARABIC),
            isOfflineOnly = preferences[PreferenceKeys.IS_OFFLINE_ONLY] ?: false,
            overlayOpacity = preferences[PreferenceKeys.OVERLAY_OPACITY] ?: 0.85f,
            fontSizeSp = preferences[PreferenceKeys.FONT_SIZE_SP] ?: 16,
            easternArabicNumerals = preferences[PreferenceKeys.EASTERN_ARABIC_NUMERALS] ?: false,
            autoStartOnBilibili = preferences[PreferenceKeys.AUTO_START_BILIBILI] ?: true,
            showOriginalUnderlay = preferences[PreferenceKeys.SHOW_ORIGINAL_UNDERLAY] ?: true,
            subtitleLinesCount = preferences[PreferenceKeys.SUBTITLE_LINES] ?: 3
        )
    }

    suspend fun setOcrEngine(engine: OcrEngine) {
        context.dataStore.edit { it[PreferenceKeys.OCR_ENGINE] = engine.name }
    }

    suspend fun setSttEngine(engine: SttEngine) {
        context.dataStore.edit { it[PreferenceKeys.STT_ENGINE] = engine.name }
    }

    suspend fun setTranslationEngine(engine: TranslationEngine) {
        context.dataStore.edit { it[PreferenceKeys.TRANSLATION_ENGINE] = engine.name }
    }

    suspend fun setTargetLanguage(language: TargetLanguage) {
        context.dataStore.edit { it[PreferenceKeys.TARGET_LANGUAGE] = language.name }
    }

    suspend fun setOfflineOnly(offline: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.IS_OFFLINE_ONLY] = offline }
    }

    suspend fun setOverlayOpacity(opacity: Float) {
        context.dataStore.edit { it[PreferenceKeys.OVERLAY_OPACITY] = opacity.coerceIn(0.0f, 0.95f) }
    }

    suspend fun setFontSizeSp(fontSize: Int) {
        context.dataStore.edit { it[PreferenceKeys.FONT_SIZE_SP] = fontSize }
    }

    suspend fun setEasternArabicNumerals(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.EASTERN_ARABIC_NUMERALS] = enabled }
    }

    suspend fun setAutoStartOnBilibili(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.AUTO_START_BILIBILI] = enabled }
    }

    suspend fun setShowOriginalUnderlay(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_ORIGINAL_UNDERLAY] = enabled }
    }
}
