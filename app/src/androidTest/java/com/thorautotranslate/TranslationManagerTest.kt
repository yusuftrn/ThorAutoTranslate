package com.thorautotranslate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationManagerTest {
    @Test fun closingOneSessionDoesNotBreakTheNextAndCachedTranslationIsImmediate() = runBlocking {
        val first = TranslationManager(TranslationConfig("en", "tr", TranslationMode.FREE, ""), InstrumentationRegistry.getInstrumentation().targetContext)
        val next = TranslationManager(TranslationConfig("en", "tr", TranslationMode.FREE, ""), InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            assertTrue(first.translate("The gate is locked.").contains("kapı", true))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { first.close() }
            val translated = next.translate("Find Bonnie at the station.")
            assertTrue(translated.isNotBlank())
            assertEquals(translated, next.translate("Find Bonnie at the station."))
        } finally { first.close(); next.close() }
    }
    @Test fun offlineTranslationPreparesAndReusesItsModel() = runBlocking {
        val translator = TranslationManager(TranslationConfig("en", "tr", TranslationMode.OFFLINE, ""), InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            val translated = withTimeout(90000) { translator.translate("The gate is locked.") }
            assertTrue("Expected a Turkish offline translation: $translated", translated.contains("kapı", true))
            assertEquals(translated, translator.translate("The gate is locked."))
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { translator.close() } }
    }
    @Test fun cancellingAnActiveRequestFromMainDoesNotBreakTheServiceThread() = runBlocking {
        val translator = TranslationManager(TranslationConfig("en", "tr", TranslationMode.FREE, ""), InstrumentationRegistry.getInstrumentation().targetContext)
        val request = async { runCatching { translator.translate("Follow the river to the old bridge.") } }
        delay(50)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            request.cancel()
            translator.close()
        }
        request.join()
    }
}
