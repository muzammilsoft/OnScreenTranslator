package com.example.presentation.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.BidiFormatter
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.local.prefs.AppSettings
import com.example.domain.state.SubtitleItem
import com.example.domain.state.TranslatedNode
import java.util.Locale

/**
 * Custom floating UI element that renders semi-transparent rounded badges covering Chinese text.
 */
class UiBadgeView(context: Context) : TextView(context) {
    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        elevation = 6f
    }

    fun bind(node: TranslatedNode, settings: AppSettings) {
        val bidi = BidiFormatter.getInstance(Locale("ar"))
        var displayText = if (node.isRtl) bidi.unicodeWrap(node.translatedText) else node.translatedText

        if (settings.easternArabicNumerals) {
            displayText = convertToArabicNumerals(displayText)
        }

        text = displayText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, (settings.fontSizeSp - 2).coerceAtLeast(11).toFloat())
        setTextColor(Color.parseColor("#F8FAFC"))
        typeface = Typeface.DEFAULT_BOLD
        textDirection = if (node.isRtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR

        // Semi-transparent rounded background cover
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f
            val alphaInt = (settings.overlayOpacity * 255).toInt().coerceIn(40, 240)
            setColor(Color.argb(alphaInt, 28, 27, 31)) // Bento Background #1C1B1F
            setStroke(2, Color.argb(200, 208, 188, 255)) // Bento Lavender #D0BCFF
        }
        background = bg
        setPadding(12, 6, 12, 6)
    }

    private fun convertToArabicNumerals(input: String): String {
        val englishToEasternArabic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val builder = StringBuilder()
        for (ch in input) {
            if (ch in '0'..'9') {
                builder.append(englishToEasternArabic[ch - '0'])
            } else {
                builder.append(ch)
            }
        }
        return builder.toString()
    }
}

/**
 * Draggable Floating Subtitle Box containing up to 3 scrolling sentences with smooth fade and interim updates.
 */
class FloatingSubtitleContainer(
    context: Context,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val linesLayout: LinearLayout
    private val headerBar: LinearLayout
    private val titleText: TextView
    private var initialX = 0f
    private var initialY = 0f

    init {
        // Main container background
        val containerBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(Color.argb(240, 28, 27, 31)) // Bento background #1C1B1F
            setStroke(2, Color.argb(180, 208, 188, 255)) // Bento Lavender #D0BCFF
        }
        background = containerBg
        elevation = 12f
        setPadding(24, 16, 24, 20)

        // Header bar with drag pill and status
        headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }

        val pill = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(60, 8).apply {
                marginEnd = 16
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4f
                setColor(Color.parseColor("#49454F"))
            }
        }

        titleText = TextView(context).apply {
            text = "● ZOOL-AI • ترجمة بيلي بيلي المباشرة"
            setTextColor(Color.parseColor("#D0BCFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        headerBar.addView(pill)
        headerBar.addView(titleText)

        // Subtitle lines layout
        linesLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerBar)
            addView(linesLayout)
        }

        addView(rootLayout)

        // Touch handling for dragging the subtitle bar smoothly
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    initialY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialX
                    val dy = event.rawY - initialY
                    initialX = event.rawX
                    initialY = event.rawY
                    onDrag(dx, dy)
                    true
                }
                else -> false
            }
        }
    }

    fun updateSubtitles(subtitles: List<SubtitleItem>, settings: AppSettings) {
        linesLayout.removeAllViews()
        val bidi = BidiFormatter.getInstance(Locale("ar"))

        val maxLines = settings.subtitleLinesCount.coerceIn(1, 3)
        val displayList = subtitles.takeLast(maxLines)

        if (displayList.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "في انتظار بدء تشغيل الصوت..."
                setTextColor(Color.parseColor("#94A3B8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, (settings.fontSizeSp - 2).toFloat())
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 10)
            }
            linesLayout.addView(emptyTv)
            return
        }

        for (item in displayList) {
            val lineTv = TextView(context).apply {
                val formatted = if (item.isInterim) {
                    "⏳ ${item.originalText} (جارِ الترجمة...)"
                } else {
                    bidi.unicodeWrap(item.translatedText)
                }

                text = formatted
                setTextColor(if (item.isInterim) Color.parseColor("#FCD34D") else Color.parseColor("#FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSizeSp.toFloat())
                typeface = if (item.isInterim) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
                textDirection = if (item.isInterim) View.TEXT_DIRECTION_LTR else View.TEXT_DIRECTION_RTL
                setPadding(0, 6, 0, 6)
            }
            linesLayout.addView(lineTv)
        }
    }
}
