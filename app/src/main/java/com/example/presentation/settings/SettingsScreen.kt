package com.example.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.prefs.AppSettings
import com.example.domain.state.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onRequestMediaProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val translationState by viewModel.translationState.collectAsStateWithLifecycle()
    val isServiceActive by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val cacheCount by viewModel.cacheCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoLavender),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "Zool-AI Logo",
                                tint = BentoDeepViolet,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Zool-AI • زولاي",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = BentoTextPure
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isServiceActive) BentoNeonGreen.copy(alpha = 0.2f) else BentoBorder)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isServiceActive) "ON-DEVICE LIVE" else "STANDBY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isServiceActive) BentoNeonGreen else BentoTextMuted
                                    )
                                }
                            }
                            Text(
                                text = "Bilibili Real-Time Floating Translator",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = BentoTextMuted
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.checkPermissions() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoCardBg)
                            .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                            .testTag("refresh_permissions_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = BentoTextWhite, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BentoBackground
                )
            )
        },
        containerColor = BentoBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Bento Hero Card: Pipeline State
            item {
                BentoHeroStateCard(
                    state = translationState,
                    isActive = isServiceActive,
                    onToggle = {
                        if (!permissions.hasOverlayPermission) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            viewModel.toggleService()
                            viewModel.startFloatingService()
                        }
                    }
                )
            }

            // 2. Bento 2-Column Grid: Latency & VAD Telemetry
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Latency Tile
                    BentoLatencyTile(
                        modifier = Modifier.weight(1f),
                        isActive = isServiceActive
                    )

                    // VAD Audio Logic Tile
                    BentoVadAudioTile(
                        modifier = Modifier.weight(1f),
                        isActive = isServiceActive
                    )
                }
            }

            // 3. Bento Card: Active Engines Specs
            item {
                BentoActiveEnginesCard(settings = settings)
            }

            // 4. Bento Card: System Permissions Verification
            item {
                BentoPermissionsCard(
                    status = permissions,
                    onGrantOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onGrantAccessibility = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    onRequestAudio = onRequestMediaProjection
                )
            }

            // 5. Bento Card: Live Bilibili Simulator
            item {
                BentoBilibiliPlaygroundCard(
                    onSimulateUi = { viewModel.simulateBilibiliUiCapture() },
                    onSimulateSpeech = { phrase -> viewModel.simulateSpeechSentence(phrase) }
                )
            }

            // 6. Bento Card: Live Subtitle Appearance & Style
            item {
                BentoAppearanceCard(
                    settings = settings,
                    onOpacityChange = { viewModel.setOverlayOpacity(it) },
                    onFontSizeChange = { viewModel.setFontSize(it) },
                    onNumeralsToggle = { viewModel.setEasternArabicNumerals(it) },
                    onTargetLangChange = { viewModel.updateTargetLanguage(it) }
                )
            }

            // 7. Bento Card: AI Providers Configuration
            item {
                BentoProvidersCard(
                    settings = settings,
                    onOcrChange = { viewModel.updateOcrEngine(it) },
                    onSttChange = { viewModel.updateSttEngine(it) },
                    onTranslationChange = { viewModel.updateTranslationEngine(it) },
                    onOfflineOnlyToggle = { viewModel.setOfflineOnly(it) }
                )
            }

            // 8. Bento Card: Model Zoo & Offline Assets
            item {
                BentoModelZooCard(
                    models = models,
                    onDownload = { modelId -> viewModel.downloadModel(modelId) },
                    onDelete = { modelId -> viewModel.deleteModel(modelId) }
                )
            }

            // 9. Bento Card: Cache & Storage Stats
            item {
                BentoCacheStatsCard(
                    cacheCount = cacheCount,
                    onClearCache = { viewModel.clearCache() }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Bento Components
// -------------------------------------------------------------

@Composable
fun BentoHeroStateCard(
    state: TranslationState,
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = BentoLavender),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CURRENT PIPELINE STATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = BentoDeepViolet.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    val stateTitle = when (state) {
                        is TranslationState.Idle -> if (isActive) "LISTENING & READY" else "SYSTEM IDLE"
                        is TranslationState.CapturingUi -> "CAPTURING_UI"
                        is TranslationState.CapturingAudio -> "CAPTURING_AUDIO"
                        is TranslationState.Processing -> "PROCESSING_PIPELINE"
                        is TranslationState.Displaying -> "STREAMING_OVERLAY"
                        is TranslationState.Error -> "SYSTEM_ERROR"
                    }
                    Text(
                        text = stateTitle,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = BentoDeepViolet
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoDeepViolet),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.GraphicEq else Icons.Default.Pause,
                        contentDescription = null,
                        tint = BentoLavender,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Description and pill tags
            val statusDesc = when (state) {
                is TranslationState.Idle -> if (isActive) "محرك الترجمة يترقب نصوص أو صوت تطبيق بيلي بيلي..." else "محرك الترجمة متوقف. اضغط تشغيل للبدء."
                is TranslationState.CapturingUi -> "قراءة شجرة واجهة المستخدم الصينية (Accessibility Extraction)..."
                is TranslationState.CapturingAudio -> "التقاط تيار الصوت الداخلي لمعالجة التعرف الصوتي (Sherpa-ONNX)..."
                is TranslationState.Processing -> state.task
                is TranslationState.Displaying -> "عرض ${state.activeUiBadges} وسوم واجهة و${state.subtitleCount} شريط ترجمة مصاحبة"
                is TranslationState.Error -> "تنبيه: ${state.message}"
            }

            Text(
                text = statusDesc,
                fontSize = 13.sp,
                color = BentoDeepViolet.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )

            // Bottom action row inside Hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BentoDeepViolet.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isActive) "● AUDIO PIPELINE ACTIVE" else "○ STANDBY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoDeepViolet
                        )
                    }
                }

                Button(
                    onClick = onToggle,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) BentoRed else BentoDeepViolet,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("toggle_service_button")
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isActive) "إيقاف المحرك" else "تشغيل الترجمة",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BentoLatencyTile(
    modifier: Modifier = Modifier,
    isActive: Boolean
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        modifier = modifier
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LATENCY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = BentoTextMuted
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) BentoNeonGreen else BentoTextMuted)
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (isActive) "42" else "0",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = BentoTextPure
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "ms",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoNeonGreen,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Progress visualizer
            LinearProgressIndicator(
                progress = { if (isActive) 0.25f else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BentoNeonGreen,
                trackColor = BentoBackground
            )

            Text(
                text = "ONNX Int8 Offline Speed",
                fontSize = 11.sp,
                color = BentoTextMuted
            )
        }
    }
}

@Composable
fun BentoVadAudioTile(
    modifier: Modifier = Modifier,
    isActive: Boolean
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        modifier = modifier
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VAD SPEECH",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = BentoTextMuted
                )
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isActive) BentoNeonGreen else BentoTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Equalizer Bars Visualizer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val heights = if (isActive) listOf(0.4f, 0.85f, 0.6f, 1f, 0.7f, 0.45f) else listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f)
                heights.forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight(h)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isActive) BentoNeonGreen else BentoBorder)
                    )
                }
            }

            Text(
                text = if (isActive) "Silence: 240ms • Silero VAD" else "VAD Inactive",
                fontSize = 11.sp,
                color = BentoTextMuted
            )
        }
    }
}

@Composable
fun BentoActiveEnginesCard(settings: AppSettings) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoAccentCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = BentoLavender, modifier = Modifier.size(20.dp))
                    Text(
                        text = "ACTIVE AI ENGINES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = BentoLavender
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BentoNeonGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (settings.isOfflineOnly) "OFFLINE ONLY" else "HYBRID-FIRST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoNeonGreen
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // OCR Pill
                EngineMiniTag(
                    modifier = Modifier.weight(1f),
                    label = "OCR (Vision)",
                    value = settings.ocrEngine.displayName
                )
                // STT Pill
                EngineMiniTag(
                    modifier = Modifier.weight(1f),
                    label = "STT (Audio)",
                    value = settings.sttEngine.displayName
                )
                // Translation Pill
                EngineMiniTag(
                    modifier = Modifier.weight(1f),
                    label = "Translator",
                    value = settings.translationEngine.displayName
                )
            }
        }
    }
}

@Composable
fun EngineMiniTag(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BentoTextMuted)
            Text(
                value,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = BentoLavender,
                maxLines = 1
            )
        }
    }
}

@Composable
fun BentoPermissionsCard(
    status: PermissionStatus,
    onGrantOverlay: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onRequestAudio: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = BentoLavender)
                Text(
                    text = "أذونات النظام الأساسية (Required Permissions)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPure
                    )
                )
            }

            BentoPermissionItem(
                title = "الظهور فوق التطبيقات (Overlay)",
                description = "لعرض الترجمة والترجمة المصاحبة فوق تطبيق بيلي بيلي",
                isGranted = status.hasOverlayPermission,
                onGrant = onGrantOverlay
            )

            BentoPermissionItem(
                title = "إمكانية الوصول (Accessibility)",
                description = "لقراءة نصوص الواجهة الصينية لحظياً بدون التقاط الشاشة",
                isGranted = status.hasAccessibilityPermission,
                onGrant = onGrantAccessibility
            )

            BentoPermissionItem(
                title = "التقاط الصوت الداخلي (MediaProjection)",
                description = "لالتقاط صوت الفيديو الصيني بدقة دون تشويش الميكروفون",
                isGranted = status.hasAudioPermission,
                onGrant = onRequestAudio
            )
        }
    }
}

@Composable
fun BentoPermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoBackground)
            .border(1.dp, BentoBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = BentoTextPure
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = BentoTextMuted)
            )
        }

        Spacer(Modifier.width(10.dp))

        if (isGranted) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BentoNeonGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = BentoNeonGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BentoLavender, contentColor = BentoDeepViolet),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("منح الإذن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BentoBilibiliPlaygroundCard(
    onSimulateUi: () -> Unit,
    onSimulateSpeech: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = BentoLavender)
                Text(
                    text = "مختبر محاكاة Bilibili التفاعلي",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPure
                    )
                )
            }

            Text(
                text = "اضغط لاختبار استخراج النصوص وترجمتها الفورية فوق الشاشة والصوت الداخلي:",
                style = MaterialTheme.typography.bodySmall.copy(color = BentoTextMuted)
            )

            Button(
                onClick = onSimulateUi,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BentoDeepViolet, contentColor = BentoLavender),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoLavender.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            ) {
                Icon(Icons.Default.ViewQuilt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("محاكاة ترجمة أزرار الواجهة (动态 / 点赞 / 关注)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "محاكاة صوت الفيديو (Streaming Speech -> Subtitles):",
                style = MaterialTheme.typography.labelSmall.copy(color = BentoTextMuted)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSimulateSpeech("大家好欢迎来到我的频道") },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BentoLavender),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("大家好欢迎...", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = { onSimulateSpeech("点赞投币收藏不要忘了哦") },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BentoLavender),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("点赞投币...", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun BentoAppearanceCard(
    settings: AppSettings,
    onOpacityChange: (Float) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onNumeralsToggle: (Boolean) -> Unit,
    onTargetLangChange: (TargetLanguage) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = BentoLavender)
                Text(
                    text = "المظهر والتخصيص المباشر (Bento Preview)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPure
                    )
                )
            }

            // Live Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoBackground)
                    .border(1.dp, BentoBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "معاينة شريط الترجمة المصاحبة المباشرة",
                        fontSize = 11.sp,
                        color = BentoTextMuted
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = settings.overlayOpacity))
                            .border(1.dp, BentoLavender.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (settings.easternArabicNumerals) "مرحباً بكم في فيديو اليوم • الحلقة ١" else "مرحباً بكم في فيديو اليوم • الحلقة 1",
                            color = BentoTextPure,
                            fontSize = settings.fontSizeSp.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Opacity Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("شفافية خلفية الترجمة:", style = MaterialTheme.typography.bodySmall.copy(color = BentoTextMuted))
                    Text("${(settings.overlayOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(color = BentoLavender, fontWeight = FontWeight.Bold))
                }
                Slider(
                    value = settings.overlayOpacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.2f..0.95f,
                    colors = SliderDefaults.colors(
                        thumbColor = BentoLavender,
                        activeTrackColor = BentoLavender,
                        inactiveTrackColor = BentoBorder
                    )
                )
            }

            // Font Size Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("حجم الخط:", style = MaterialTheme.typography.bodySmall.copy(color = BentoTextMuted))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(14, 16, 18, 22).forEach { size ->
                        FilterChip(
                            selected = settings.fontSizeSp == size,
                            onClick = { onFontSizeChange(size) },
                            label = { Text("${size}sp") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BentoLavender,
                                selectedLabelColor = BentoDeepViolet,
                                containerColor = BentoBackground,
                                labelColor = BentoTextWhite
                            )
                        )
                    }
                }
            }

            // Eastern Arabic Numerals Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("الأرقام العربية المشرقية (١، ٢، ٣)", style = MaterialTheme.typography.bodyMedium.copy(color = BentoTextPure))
                    Text("تحويل الأرقام الإنجليزية إلى العربية", style = MaterialTheme.typography.bodySmall.copy(color = BentoTextMuted))
                }
                Switch(
                    checked = settings.easternArabicNumerals,
                    onCheckedChange = onNumeralsToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BentoLavender,
                        checkedTrackColor = BentoDeepViolet,
                        uncheckedThumbColor = BentoTextMuted,
                        uncheckedTrackColor = BentoBackground
                    )
                )
            }

            // Target Language Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("لغة الترجمة المستهدفة:", style = MaterialTheme.typography.bodySmall.copy(color = BentoTextMuted))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetLanguage.values().forEach { lang ->
                        FilterChip(
                            selected = settings.targetLanguage == lang,
                            onClick = { onTargetLangChange(lang) },
                            label = { Text(lang.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BentoLavender,
                                selectedLabelColor = BentoDeepViolet,
                                containerColor = BentoBackground,
                                labelColor = BentoTextWhite
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoProvidersCard(
    settings: AppSettings,
    onOcrChange: (OcrEngine) -> Unit,
    onSttChange: (SttEngine) -> Unit,
    onTranslationChange: (TranslationEngine) -> Unit,
    onOfflineOnlyToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = BentoLavender)
                Text(
                    text = "محركات الذكاء الاصطناعي (Hybrid-First Config)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPure
                    )
                )
            }

            // Offline Only Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoBackground)
                    .border(1.dp, BentoBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("الوضع غير المتصل بالكامل (Offline Only)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = BentoTextPure))
                    Text("حظر أي اتصال بالإنترنت والاعتماد فقط على النماذج المدمجة", style = MaterialTheme.typography.bodySmall.copy(color = BentoTextMuted))
                }
                Switch(
                    checked = settings.isOfflineOnly,
                    onCheckedChange = onOfflineOnlyToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BentoLavender,
                        checkedTrackColor = BentoDeepViolet,
                        uncheckedThumbColor = BentoTextMuted,
                        uncheckedTrackColor = BentoCardBg
                    )
                )
            }

            // OCR Engine
            Text("محرك التعرف البصري على النصوص (OCR):", style = MaterialTheme.typography.labelSmall.copy(color = BentoTextMuted))
            OcrEngine.values().forEach { engine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (settings.ocrEngine == engine) BentoAccentCard else Color.Transparent)
                        .clickable { onOcrChange(engine) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.ocrEngine == engine,
                        onClick = { onOcrChange(engine) },
                        colors = RadioButtonDefaults.colors(selectedColor = BentoLavender)
                    )
                    Text(engine.displayName, style = MaterialTheme.typography.bodyMedium.copy(color = BentoTextPure))
                }
            }

            // STT Engine
            Text("محرك تحويل الصوت إلى نص (Streaming STT):", style = MaterialTheme.typography.labelSmall.copy(color = BentoTextMuted))
            SttEngine.values().forEach { engine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (settings.sttEngine == engine) BentoAccentCard else Color.Transparent)
                        .clickable { onSttChange(engine) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.sttEngine == engine,
                        onClick = { onSttChange(engine) },
                        colors = RadioButtonDefaults.colors(selectedColor = BentoLavender)
                    )
                    Text(engine.displayName, style = MaterialTheme.typography.bodyMedium.copy(color = BentoTextPure))
                }
            }

            // Translation Engine
            Text("محرك الترجمة الآلية (Translation Engine):", style = MaterialTheme.typography.labelSmall.copy(color = BentoTextMuted))
            TranslationEngine.values().forEach { engine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (settings.translationEngine == engine) BentoAccentCard else Color.Transparent)
                        .clickable { onTranslationChange(engine) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.translationEngine == engine,
                        onClick = { onTranslationChange(engine) },
                        colors = RadioButtonDefaults.colors(selectedColor = BentoLavender)
                    )
                    Text(engine.displayName, style = MaterialTheme.typography.bodyMedium.copy(color = BentoTextPure))
                }
            }
        }
    }
}

@Composable
fun BentoModelZooCard(
    models: List<com.example.data.model.ModelPackage>,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BentoLavender)
                Text(
                    text = "إدارة النماذج غير المتصلة (Model Zoo)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPure
                    )
                )
            }

            models.forEach { model ->
                BentoModelItemView(model = model, onDownload = { onDownload(model.id) }, onDelete = { onDelete(model.id) })
            }
        }
    }
}

@Composable
fun BentoModelItemView(
    model: com.example.data.model.ModelPackage,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoBackground)
            .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = BentoTextPure))
                Text(
                    text = "${model.fileName} • ${(model.sizeBytes / (1024 * 1024))}MB",
                    style = MaterialTheme.typography.labelSmall.copy(color = BentoTextMuted)
                )
            }

            if (model.isInstalled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BentoNeonGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("مثبت", style = MaterialTheme.typography.labelSmall.copy(color = BentoNeonGreen, fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = BentoTextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            } else if (model.isDownloading) {
                Text("${(model.downloadProgress * 100).toInt()}%", color = BentoLavender, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            } else {
                Button(
                    onClick = onDownload,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoLavender, contentColor = BentoDeepViolet),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("تنزيل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (model.isDownloading) {
            LinearProgressIndicator(
                progress = { model.downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = BentoLavender,
                trackColor = BentoBorder
            )
        }
    }
}

@Composable
fun BentoCacheStatsCard(
    cacheCount: Int,
    onClearCache: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ذاكرة التخزين المؤقت للترجمات (Room)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPure
                    )
                )
                Text(
                    text = "تم تخزين $cacheCount مصطلح وترجمة (LRU 7-Days TTL)",
                    style = MaterialTheme.typography.bodySmall.copy(color = BentoTextMuted)
                )
            }

            OutlinedButton(
                onClick = onClearCache,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BentoRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoRed.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("مسح", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
