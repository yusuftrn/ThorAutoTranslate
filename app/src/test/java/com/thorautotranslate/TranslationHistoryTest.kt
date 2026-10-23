package com.thorautotranslate

import org.junit.Assert.*
import org.junit.Test

class TranslationHistoryTest {
    @Test fun lateRepliesCannotReplaceTheNewestLineOrChangeReadingOrder() {
        val history = TranslationHistory()
        history.add(3, "Third line", "Üç")
        history.add(1, "First line", "Bir")
        history.add(2, "Second line", "İki")
        assertEquals(listOf("Bir", "İki", "Üç"), history.texts)
        history.add(4, "Fourth line", "Dört")
        history.add(0, "Ancient line", "Sıfır")
        assertEquals(listOf("İki", "Üç", "Dört"), history.texts)
    }
    @Test fun aLateDuplicateCannotOverwriteANewerTranslation() {
        val history = TranslationHistory()
        history.add(1, "Find Bonnie", "Bonnie'yi bul")
        history.add(3, "Find Bonnie!", "Bonnie'yi bul.")
        history.add(2, "Find Bonnie", "Eski yanıt")
        assertEquals(listOf("Bonnie'yi bul."), history.texts)
        history.clear()
        assertTrue(history.texts.isEmpty())
    }
    @Test fun aTypewriterContinuationReplacesItsFragmentAndLateFragmentsStayOut() {
        val history = TranslationHistory()
        history.add(1, "Follow Bonnie to the old bridge", "Bonnie'yi eski köprüye takip et")
        history.add(3, "Follow Bonnie to the old bridge at dawn.", "Bonnie'yi şafakta eski köprüye takip et.")
        history.add(2, "Follow Bonnie to the old bridge", "Gecikmiş yarım metin")
        assertEquals(listOf("Bonnie'yi şafakta eski köprüye takip et."), history.texts)
        history.add(4, "Go to the station.", "İstasyona git.")
        assertEquals(2, history.texts.size)
    }
    @Test fun aLongContinuationDoesNotAddAnExtraHistoryRowOrLoseItsEnding() {
        val history = TranslationHistory()
        val fragment = "Follow the river to the old bridge"
        history.add(1, fragment, "ilk parça")
        history.add(2, fragment + " and wait for Bonnie before riding to the station at dawn.", "tam uzun metin")
        history.add(3, fragment, "eksik OCR")
        assertEquals(listOf("tam uzun metin"), history.texts)
    }
    @Test fun unrelatedLongLinesAndShortRepliesRemainSeparate() {
        val history = TranslationHistory()
        history.add(1, "Yes!", "Evet!")
        history.add(2, "Yes, I will follow you to the station.", "Evet, istasyona kadar seni takip edeceğim.")
        history.add(3, "Find Bonnie at the station before sunset.", "Gün batmadan Bonnie'yi istasyonda bul.")
        assertEquals(3, history.texts.size)
    }
}
