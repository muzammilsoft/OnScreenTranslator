package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.managers.ControlManager
import com.example.presentation.controls.FloatingControlView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * FloatingControlService manages the system-level floating action button (FAB)
 * that stays on screen across apps to toggle real-time translation with a single tap.
 */
class FloatingControlService : Service() {

    private val CHANNEL_ID = "floating_control_channel"
    private val NOTIFICATION_ID = 1001

    private lateinit var windowManager: WindowManager
    private lateinit var controlManager: ControlManager
    private var floatingView: FloatingControlView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        controlManager = ControlManager.getInstance(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        if (canDrawOverlays()) {
            setupFloatingView()
            observeState()
        }
    }

    private fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun setupFloatingView() {
        val displaySize = Point()
        windowManager.defaultDisplay.getSize(displaySize)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = displaySize.x - (70 * resources.displayMetrics.density).toInt()
            y = (displaySize.y * 0.45f).toInt()
        }

        floatingView = FloatingControlView(
            context = this,
            onSingleTap = {
                val newState = controlManager.toggleService(null)
                floatingView?.updateActiveState(newState)
            },
            onLongPress = {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            },
            onDrag = { dx, dy ->
                layoutParams?.let { lp ->
                    lp.x += dx.toInt()
                    lp.y += dy.toInt()
                    try {
                        windowManager.updateViewLayout(floatingView, lp)
                    } catch (ignored: Exception) {}
                }
            }
        )

        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            floatingView = null
        }
    }

    private fun observeState() {
        serviceScope.launch {
            controlManager.isServiceActive.collectLatest { isActive ->
                floatingView?.updateActiveState(isActive)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zool-AI • الترجمة العائمة نشطة")
            .setContentText("انقر على الزر العائم للترجمة أو اضغط مطولاً للإعدادات")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (ignored: Exception) {}
        }
        floatingView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
