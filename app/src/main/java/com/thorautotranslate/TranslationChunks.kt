package com.thorautotranslate

/** Keep every part of long OCR passages within online request limits, without splitting Unicode pairs. */
internal fun translationChunks(text: String, limit: Int = 800): List<String> {
    require(limit >= 2)
    val chunks = mutableListOf<String>()
    var remaining = text.trim()
    while (remaining.length > limit) {
        val boundary = remaining.lastIndexOfAny(charArrayOf(' ', '\n'), limit - 1)
        var end = if (boundary >= limit / 2) boundary + 1 else limit
        if (remaining[end - 1].isHighSurrogate()) end--
        chunks.add(remaining.substring(0, end).trim())
        remaining = remaining.substring(end).trimStart()
    }
    if (remaining.isNotBlank()) chunks.add(remaining)
    return chunks
}
