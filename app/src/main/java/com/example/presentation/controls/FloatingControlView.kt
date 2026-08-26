package com.example.presentation.controls

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Floating Action Button (FAB) for the translator service with dragging and click/long-press listeners.
 */
class FloatingControlView(
    context: Context,
    private val onSingleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onDrag: (dx: Float, dy: Float) -> Unit
) : FrameLayout(context) {

    private val fabButton: TextView
    private var initialX = 0f
    private var initialY = 0f
    private var isDragging = false
    private var pressStartTime = 0L

    init {
        fabButton = TextView(context).apply {
            text = "文/ع"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            elevation = 14f

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2B2930")) // Bento Card Bg #2B2930
                setStroke(4, Color.parseColor("#49454F"))
            }
            background = bg
        }

        val sizePx = (56 * context.resources.displayMetrics.density).toInt()
        val lp = LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER
        }
        addView(fabButton, lp)

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressStartTime = System.currentTimeMillis()
                    initialX = event.rawX
                    initialY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialX
                    val dy = event.rawY - initialY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        initialX = event.rawX
                        initialY = event.rawY
                        onDrag(dx, dy)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - pressStartTime
                    if (!isDragging) {
                        if (duration > 600) {
                            onLongPress()
                        } else {
                            onSingleTap()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun updateActiveState(isActive: Boolean) {
        val bg = fabButton.background as? GradientDrawable ?: return
        if (isActive) {
            bg.setColor(Color.parseColor("#B3261E")) // Bento Red #B3261E when active
            bg.setStroke(4, Color.parseColor("#D0BCFF")) // Lavender outline
            fabButton.text = "🔴"
        } else {
            bg.setColor(Color.parseColor("#2B2930")) // Bento Card Bg when idle
            bg.setStroke(4, Color.parseColor("#49454F"))
            fabButton.text = "文/ع"
        }
    }
}
