package com.example.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.domain.managers.ControlManager

/**
 * AccessibilityTranslationService listens to Bilibili UI hierarchy changes,
 * extracts on-screen Chinese text, and passes root nodes to ControlManager for debounced processing.
 */
class AccessibilityTranslationService : AccessibilityService() {

    private lateinit var controlManager: ControlManager

    override fun onCreate() {
        super.onCreate()
        controlManager = ControlManager.getInstance(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString()
        controlManager.notifyPackageChanged(pkgName)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val root = rootInActiveWindow
                controlManager.captureManager.processAccessibilityRoot(root)
            }
        }
    }

    override fun onInterrupt() {
        // Handle interruption gracefully
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
