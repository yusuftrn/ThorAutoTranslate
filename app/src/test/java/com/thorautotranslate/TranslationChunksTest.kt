package com.thorautotranslate

import org.junit.Assert.*
import org.junit.Test

class TranslationChunksTest {
    @Test fun longParagraphsKeepEveryWordAndShortPassagesStayWhole() {
        val text = (1..600).joinToString(" ") { "word$it" }
        val chunks = translationChunks(text)
        assertTrue(chunks.size > 3)
        assertTrue(chunks.all { it.length <= 800 })
        assertEquals(text, chunks.joinToString(" "))
        assertEquals(listOf("The gate is locked."), translationChunks("The gate is locked."))
    }
    @Test fun cjkAndSupplementaryCharactersAreNeverTruncatedOrSplit() {
        val text = "扉が閉まっています。🎮".repeat(200)
        val chunks = translationChunks(text, 31)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 31 && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() })
    }
}
