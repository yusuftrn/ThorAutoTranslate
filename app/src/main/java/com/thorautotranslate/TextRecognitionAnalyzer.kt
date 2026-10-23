package com.thorautotranslate

import android.graphics.Rect
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TextRecognitionAnalyzer(language: String) {
    private val recognizer = when (language) {
        "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        "hi" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private var previousFrame: Bitmap? = null
    private var previousSubtitleOnly = false
    private var previousText = ""
    private var focus: Rect? = null
    private var focusPixels: Bitmap? = null
    private var nextDiscovery = 0L
    private var fallback = 0
    @Volatile private var reading = false
    @Volatile private var closed = false
    private var released = false
    internal var ocrPasses = 0
        private set

    /** Owns the screenshot; pixel copies/comparisons and OCR preparation stay off the UI thread. */
    suspend fun read(screenshot: Bitmap, subtitleOnly: Boolean): String {
        var retained = false
        try {
            check(!closed && !reading)
            reading = true
            return withContext(Dispatchers.Default) {
                if (previousText.isNotBlank() && previousSubtitleOnly == subtitleOnly && previousFrame?.sameAs(screenshot) == true)
                    return@withContext previousText
                if (previousSubtitleOnly != subtitleOnly || previousFrame?.width != screenshot.width || previousFrame?.height != screenshot.height) {
                    focus = null; focusPixels?.recycle(); focusPixels = null; nextDiscovery = 0
                }
                val text = recognize(screenshot, subtitleOnly)
                currentCoroutineContext().ensureActive()
                if (!closed) {
                    previousFrame?.recycle()
                    previousFrame = screenshot
                    previousText = text
                    previousSubtitleOnly = subtitleOnly
                    retained = true
                }
                text
            }
        } finally {
            if (!retained) screenshot.recycle()
            reading = false
            if (closed) release()
        }
    }

    private suspend fun recognize(screenshot: Bitmap, subtitleOnly: Boolean): String {
        val passesBefore = ocrPasses
        val width = screenshot.width
        val height = screenshot.height
        val now = SystemClock.elapsedRealtime()
        val discovery = now >= nextDiscovery
        val known = focus
        if (!discovery && known != null) {
            val pixels = Bitmap.createBitmap(screenshot, known.left, known.top, known.width(), known.height())
            try { if (focusPixels?.sameAs(pixels) == true) return previousText }
            finally { if (pixels !== screenshot) pixels.recycle() }
            val text = scan(screenshot, known)
            if (text.isNotBlank()) { rememberFocus(screenshot); return text }
            // A vanished/moved dialogue must be found on this frame, rather than reusing stale text.
            focus = null
        }
        val area = if (subtitleOnly) Rect(0, (height * .55f).toInt(), width, height) else Rect(0, 0, width, height)
        val text = scan(screenshot, area)
        nextDiscovery = now + 1000
        if (text.isNotBlank()) { rememberFocus(screenshot); return text }
        focus = null
        focusPixels?.recycle(); focusPixels = null
        if (subtitleOnly || ocrPasses - passesBefore >= 2) return ""
        // At most two OCR passes per frame; alternate tutorial crops without blocking the next capture.
        val areas = listOf(
            Rect(0, 0, (width * .55f).toInt(), (height * .6f).toInt()),
            Rect((width * .45f).toInt(), 0, width, (height * .6f).toInt()),
            Rect((width * .1f).toInt(), (height * .4f).toInt(), (width * .9f).toInt(), height)
        )
        val focused = scan(screenshot, areas[fallback++ % areas.size])
        if (focused.isNotBlank()) rememberFocus(screenshot)
        return focused
    }

    private fun rememberFocus(screenshot: Bitmap) {
        focusPixels?.recycle()
        focusPixels = focus?.let {
            Bitmap.createBitmap(screenshot, it.left, it.top, it.width(), it.height()).let { pixels ->
                if (pixels === screenshot) pixels.copy(Bitmap.Config.ARGB_8888, false) else pixels
            }
        }
    }

    private suspend fun scan(screenshot: Bitmap, area: Rect): String {
        currentCoroutineContext().ensureActive()
        val cropped = Bitmap.createBitmap(screenshot, area.left, area.top, area.width(), area.height())
        try {
            ocrPasses++
            val result = withContext(NonCancellable) { recognizer.process(InputImage.fromBitmap(cropped, 0)).await() }
            currentCoroutineContext().ensureActive()
            val regions = result.textBlocks.mapNotNull { block ->
                val box = block.boundingBox ?: return@mapNotNull null
                OcrRegion(block.text,
                    (area.left + box.left).toFloat() / screenshot.width,
                    (area.top + box.top).toFloat() / screenshot.height,
                    (area.left + box.right).toFloat() / screenshot.width,
                    (area.top + box.bottom).toFloat() / screenshot.height)
            }
            val text = DialogueSelector.select(regions)
            if (text.isNotBlank()) {
                val key = dialogueKey(text)
                val selected = regions.filter { dialogueKey(it.text).let { k -> k.isNotBlank() && key.contains(k) } }
                focus = null
                if (selected.isNotEmpty()) {
                    // Full width and vertical margin keep new lines/speaker names inside the tracked region.
                    val top = (selected.minOf { it.top } * screenshot.height).toInt()
                    val bottom = (selected.maxOf { it.bottom } * screenshot.height).toInt()
                    val margin = maxOf(24, screenshot.height / 12)
                    focus = Rect(0, (top - margin).coerceAtLeast(area.top), screenshot.width,
                        (bottom + margin).coerceAtMost(area.bottom))
                }
            }
            return text
        } finally { if (cropped !== screenshot) cropped.recycle() }
    }

    fun close() { closed = true; if (!reading) release() }
    @Synchronized private fun release() {
        if (released) return
        released = true
        previousFrame?.recycle(); previousFrame = null
        focusPixels?.recycle(); focusPixels = null
        recognizer.close()
    }
}
