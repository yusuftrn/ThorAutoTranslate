package com.thorautotranslate

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.widget.ScrollView
import android.widget.TextView

class TranslationPanel(service: AccessibilityService, display: Display, fontSize: Float) {
    private val displayContext = service.createDisplayContext(display)
    // The service display manager owns the accessibility overlay token; localization must not replace it.
    private val manager = displayContext.getSystemService(WindowManager::class.java)
    private val context = AppLanguage.wrap(displayContext)
    private val density = context.resources.displayMetrics.density
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        setPadding((32 * density).toInt(), (20 * density).toInt(), (32 * density).toInt(), (8 * density).toInt())
    }
    private val scroll = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        addView(content)
        contentDescription = context.getString(R.string.translation_history)
    }
    private val attribution = ImageView(context).apply {
        setImageResource(R.drawable.google_translate_attribution)
        contentDescription = "Google Translate"
        visibility = View.GONE
        setPadding(0, (8 * density).toInt(), 0, (16 * density).toInt())
    }
    private val rows = mutableListOf<TextView>()
    private var shown = emptyList<String>()
    private val baseFontSize = fontSize
    private val backdrop = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val view = FrameLayout(context).apply {
        setBackgroundColor(Color.rgb(10, 13, 12))
        val fill = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        addView(backdrop, fill)
        addView(View(context).apply { setBackgroundColor(Color.argb(220, 10, 13, 12)) }, fill)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(attribution, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, fill)
        addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_settings)
            contentDescription = context.getString(R.string.settings)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            setOnClickListener {
                service.startActivity(Intent(service, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true).putExtra("lower_screen", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId).toBundle())
            }
        }, FrameLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt(), Gravity.TOP or Gravity.END))
    }
    init {
        manager.addView(view, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START; title = "Thor Auto Translate" })
    }
    fun setBackdrop(bitmap: Bitmap) { backdrop.setImageBitmap(bitmap) }
    fun render(history: List<String>) {
        if (shown == history || history.isEmpty()) return
        attribution.visibility = View.VISIBLE
        val next = history.takeLast(3).toList()
        val newestChanged = shown.lastOrNull() != next.last()
        if (shown.isNotEmpty() && newestChanged) TransitionManager.beginDelayedTransition(content,
            TransitionSet().addTransition(ChangeBounds()).addTransition(Fade()).setDuration(180))
        if (shown.isNotEmpty() && next.last() != shown.last()) {
            if (rows.size == 3) content.removeView(rows.removeAt(0))
        }
        while (rows.size > next.size) content.removeView(rows.removeAt(0))
        while (rows.size < next.size) {
            val row = TextView(context).apply {
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setLineSpacing(4 * density, 1.1f)
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                setShadowLayer(2 * density, 0f, density, Color.BLACK)
            }
            rows.add(row)
            content.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        rows.forEachIndexed { index, row ->
            val age = next.lastIndex - index
            row.text = next[index]
            row.textSize = baseFontSize * when (age) { 0 -> 1f; 1 -> .8f; else -> .65f }
            row.setTextColor(Color.argb(when (age) { 0 -> 255; 1 -> 195; else -> 140 }, 244, 240, 232))
            row.maxLines = Int.MAX_VALUE
            row.ellipsize = null
        }
        shown = next
        if (newestChanged) scroll.post {
            // Start at the beginning of a long new translation; the whole history remains touch-scrollable.
            val newest = rows.last()
            val visible = scroll.height - content.paddingTop - content.paddingBottom
            if (newest.height > visible) scroll.scrollTo(0, newest.top - content.paddingTop)
            else scroll.fullScroll(View.FOCUS_DOWN)
        }
    }
    fun close() { manager.removeView(view) }
}
