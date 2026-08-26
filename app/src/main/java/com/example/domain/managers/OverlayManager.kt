package com.example.domain.managers

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.data.local.prefs.AppSettings
import com.example.domain.state.SubtitleItem
import com.example.domain.state.TranslatedNode
import com.example.presentation.overlay.FloatingSubtitleContainer
import com.example.presentation.overlay.UiBadgeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OverlayManager manages WindowManager overlays for UI badges, draggable subtitle box, and floating control FAB.
 */
class OverlayManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // UI Overlay (Touch-through)
    private var uiRootContainer: FrameLayout? = null
    private var uiLayoutParams: WindowManager.LayoutParams? = null

    // Subtitle Overlay (Draggable)
    private var subtitleContainer: FloatingSubtitleContainer? = null
    private var subtitleLayoutParams: WindowManager.LayoutParams? = null
    private var subtitleYOffset: Int = 120

    // FAB Overlay
    private var fabView: View? = null
    private var fabLayoutParams: WindowManager.LayoutParams? = null

    private var currentSettings: AppSettings = AppSettings()
    private val activeSubtitles = mutableListOf<SubtitleItem>()

    fun updateSettings(settings: AppSettings) {
        this.currentSettings = settings
    }

    private fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    /**
     * Initializes or gets the UI Overlay FrameLayout that spans the screen.
     */
    fun showUiOverlay(): Boolean {
        if (!canDrawOverlays()) return false

        scope.launch(Dispatchers.Main) {
            if (uiRootContainer == null) {
                uiRootContainer = FrameLayout(context)
                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                uiLayoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }

                try {
                    windowManager.addView(uiRootContainer, uiLayoutParams)
                } catch (e: Exception) {
                    uiRootContainer = null
                }
            }
        }
        return true
    }

    /**
     * Updates UI Translation Badges positioned over original Chinese elements.
     */
    fun renderUiNodes(nodes: List<TranslatedNode>) {
        if (!canDrawOverlays()) return

        scope.launch(Dispatchers.Main) {
            if (uiRootContainer == null) {
                showUiOverlay()
            }
            val container = uiRootContainer ?: return@launch
            container.removeAllViews()

            for (node in nodes) {
                val badge = UiBadgeView(context)
                badge.bind(node, currentSettings)

                val rect = node.screenBounds
                val lp = FrameLayout.LayoutParams(
                    (rect.width() * 1.15f).toInt().coerceAtLeast(80),
                    (rect.height() * 1.1f).toInt().coerceAtLeast(40)
                ).apply {
                    leftMargin = (rect.left - 5).coerceAtLeast(0)
                    topMargin = (rect.top - 2).coerceAtLeast(0)
                }
                container.addView(badge, lp)
            }
        }
    }

    /**
     * Shows and updates draggable subtitle overlay.
     */
    fun updateSubtitle(item: SubtitleItem) {
        if (!canDrawOverlays()) return

        scope.launch(Dispatchers.Main) {
            // Manage subtitle queue
            if (item.isInterim) {
                val existingIndex = activeSubtitles.indexOfFirst { it.isInterim }
                if (existingIndex != -1) {
                    activeSubtitles[existingIndex] = item
                } else {
                    activeSubtitles.add(item)
                }
            } else {
                // Final subtitle replaces any pending interim line
                activeSubtitles.removeAll { it.isInterim }
                activeSubtitles.add(item)
                if (activeSubtitles.size > 3) {
                    activeSubtitles.removeAt(0)
                }
            }

            ensureSubtitleContainer()
            subtitleContainer?.updateSubtitles(activeSubtitles, currentSettings)
        }
    }

    private fun ensureSubtitleContainer() {
        if (subtitleContainer == null) {
            subtitleContainer = FloatingSubtitleContainer(
                context = context,
                onDrag = { dx, dy ->
                    subtitleLayoutParams?.let { lp ->
                        lp.x += dx.toInt()
                        lp.y += dy.toInt()
                        try {
                            windowManager.updateViewLayout(subtitleContainer, lp)
                        } catch (ignored: Exception) {}
                    }
                },
                onClose = {
                    hideSubtitleOverlay()
                }
            )

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val displaySize = Point()
            windowManager.defaultDisplay.getSize(displaySize)

            subtitleLayoutParams = WindowManager.LayoutParams(
                (displaySize.x * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = subtitleYOffset
            }

            try {
                windowManager.addView(subtitleContainer, subtitleLayoutParams)
            } catch (e: Exception) {
                subtitleContainer = null
            }
        }
    }

    fun hideSubtitleOverlay() {
        scope.launch(Dispatchers.Main) {
            subtitleContainer?.let {
                try {
                    windowManager.removeView(it)
                } catch (ignored: Exception) {}
            }
            subtitleContainer = null
            activeSubtitles.clear()
        }
    }

    /**
     * Clears all floating window overlays within 500ms when stopped.
     */
    fun clearAllOverlays() {
        scope.launch(Dispatchers.Main) {
            uiRootContainer?.let {
                try {
                    windowManager.removeView(it)
                } catch (ignored: Exception) {}
            }
            uiRootContainer = null

            subtitleContainer?.let {
                try {
                    windowManager.removeView(it)
                } catch (ignored: Exception) {}
            }
            subtitleContainer = null
            activeSubtitles.clear()

            fabView?.let {
                try {
                    windowManager.removeView(it)
                } catch (ignored: Exception) {}
            }
            fabView = null
        }
    }
}
