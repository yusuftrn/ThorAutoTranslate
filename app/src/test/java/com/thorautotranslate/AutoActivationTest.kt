package com.thorautotranslate

import org.junit.Assert.*
import org.junit.Test

class AutoActivationTest {
    @Test fun openingTheAppOrStartingAGameEntersTranslationAutomatically() {
        assertTrue(shouldEnterTranslation(true, true, true, 1, null))
        assertFalse(shouldEnterTranslation(true, true, false, 1, null))
        assertFalse(shouldEnterTranslation(false, true, true, 1, null))
        assertFalse(shouldEnterTranslation(true, false, true, 1, null))
    }
    @Test fun explicitSettingsSurviveChangesButANewGameSessionTakesOver() {
        assertFalse(shouldEnterTranslation(true, true, true, 1, 1))
        assertTrue(shouldEnterTranslation(true, true, true, 2, 1))
    }
}
