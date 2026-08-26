package com.example.presentation.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.cache.TranslationEntity
import com.example.data.local.prefs.AppSettings
import com.example.data.model.ModelPackage
import com.example.domain.managers.ControlManager
import com.example.domain.managers.RawUiNode
import com.example.domain.state.*
import com.example.service.AudioCaptureService
import com.example.service.FloatingControlService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PermissionStatus(
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityPermission: Boolean = false,
    val hasAudioPermission: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val controlManager = ControlManager.getInstance(application)
    private val settingsRepository = controlManager.settingsRepository
    private val modelDownloadManager = controlManager.modelDownloadManager
    private val database = controlManager.database

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val translationState: StateFlow<TranslationState> = controlManager.state
    val isServiceActive: StateFlow<Boolean> = controlManager.isServiceActive
    val activeSubtitles: StateFlow<List<SubtitleItem>> = controlManager.currentSubtitles
    val activeUiNodes: StateFlow<List<TranslatedNode>> = controlManager.activeUiNodes
    val models: StateFlow<List<ModelPackage>> = modelDownloadManager.modelsState
    val cacheCount: StateFlow<Int> = database.translationDao().getCacheCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    val recentCacheItems: StateFlow<List<TranslationEntity>> = database.translationDao().getRecentTranslations().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _permissions = MutableStateFlow(PermissionStatus())
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        // Check if accessibility service is enabled
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val hasAccessibility = enabledServices.contains(context.packageName)

        val hasAudio = true // Checked dynamically on MediaProjection launch

        _permissions.value = PermissionStatus(
            hasOverlayPermission = hasOverlay,
            hasAccessibilityPermission = hasAccessibility,
            hasAudioPermission = hasAudio
        )
    }

    fun startFloatingService() {
        val context = getApplication<Application>()
        val intent = Intent(context, FloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun toggleService(mediaProjection: MediaProjection? = null) {
        controlManager.toggleService(mediaProjection)
    }

    fun updateOcrEngine(engine: OcrEngine) {
        viewModelScope.launch {
            settingsRepository.setOcrEngine(engine)
        }
    }

    fun updateSttEngine(engine: SttEngine) {
        viewModelScope.launch {
            settingsRepository.setSttEngine(engine)
        }
    }

    fun updateTranslationEngine(engine: TranslationEngine) {
        viewModelScope.launch {
            settingsRepository.setTranslationEngine(engine)
        }
    }

    fun updateTargetLanguage(lang: TargetLanguage) {
        viewModelScope.launch {
            settingsRepository.setTargetLanguage(lang)
        }
    }

    fun setOfflineOnly(offline: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOfflineOnly(offline)
        }
    }

    fun setOverlayOpacity(opacity: Float) {
        viewModelScope.launch {
            settingsRepository.setOverlayOpacity(opacity)
        }
    }

    fun setFontSize(fontSizeSp: Int) {
        viewModelScope.launch {
            settingsRepository.setFontSizeSp(fontSizeSp)
        }
    }

    fun setEasternArabicNumerals(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEasternArabicNumerals(enabled)
        }
    }

    fun setAutoStartOnBilibili(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartOnBilibili(enabled)
        }
    }

    fun downloadModel(modelId: String, requireWifi: Boolean = false) {
        viewModelScope.launch {
            modelDownloadManager.downloadModel(modelId, requireWifi)
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelDownloadManager.deleteModel(modelId)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            database.translationDao().clearAll()
        }
    }

    /**
     * Interactive Simulation: triggers Bilibili test nodes to demonstrate real-time overlay positioning and translation.
     */
    fun simulateBilibiliUiCapture() {
        viewModelScope.launch {
            val sampleNodes = listOf(
                RawUiNode("bili_btn_1", "动态", Rect(50, 200, 220, 300), "动态".hashCode()),
                RawUiNode("bili_btn_2", "热门视频", Rect(250, 200, 480, 300), "热门视频".hashCode()),
                RawUiNode("bili_btn_3", "点赞", Rect(50, 450, 200, 530), "点赞".hashCode()),
                RawUiNode("bili_btn_4", "投币", Rect(230, 450, 380, 530), "投币".hashCode()),
                RawUiNode("bili_btn_5", "收藏", Rect(410, 450, 560, 530), "收藏".hashCode())
            )
            controlManager.startTranslationService(null)
            val translatedBatch = controlManager.translationManager.translateBatch(sampleNodes.map { it.text }, "zh")
            val nodes = sampleNodes.mapIndexed { idx, raw ->
                TranslatedNode(
                    id = raw.id,
                    sourceText = raw.text,
                    translatedText = translatedBatch.getOrElse(idx) { raw.text },
                    screenBounds = raw.bounds,
                    isRtl = settings.value.targetLanguage.isRtl
                )
            }
            controlManager.overlayManager.renderUiNodes(nodes)
        }
    }

    /**
     * Interactive Simulation: triggers test video audio speech subtitle stream.
     */
    fun simulateSpeechSentence(chinesePhrase: String = "大家好欢迎来到我的频道") {
        viewModelScope.launch {
            val translated = controlManager.translationManager.translateText(chinesePhrase, "zh")
            val sub = SubtitleItem(
                id = "sim_${System.currentTimeMillis()}",
                originalText = chinesePhrase,
                translatedText = translated,
                isInterim = false,
                targetLang = settings.value.targetLanguage.code
            )
            controlManager.overlayManager.updateSubtitle(sub)
        }
    }
}
