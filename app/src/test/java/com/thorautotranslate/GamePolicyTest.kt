package com.thorautotranslate

import org.junit.Assert.*
import org.junit.Test

class GamePolicyTest {
    @Test fun automaticallyRecognizesGamesAndEmulatorsOnly() {
        assertTrue(GamePolicy.isAutomatic("org.ppsspp.ppsspp", false))
        assertTrue(GamePolicy.isAutomatic("xyz.aethersx2.tturnip", false))
        assertTrue(GamePolicy.isAutomatic("com.github.stenzek.duckstation", false))
        assertTrue(GamePolicy.isAutomatic("dev.eden.eden_emulator", false))
        assertTrue(GamePolicy.isAutomatic("any.new.game", true))
        assertFalse(GamePolicy.isAutomatic("rip.moth.cocoonshell", false))
        assertFalse(GamePolicy.isAutomatic("com.android.settings", false))
        assertFalse(GamePolicy.isAutomatic("com.retroarchfake", false))
    }
    @Test fun stabilizesAndDeduplicatesWithoutDroppingUppercaseOrUnicode() {
        val tracker = DialogueTracker()
        assertNull(tracker.accept("MENU"))
        assertEquals("MENU", tracker.accept("MENU"))
        assertNull(tracker.accept("MENU"))
        assertNull(tracker.accept("扉が閉まっている。"))
        assertEquals("扉が閉まっている。", tracker.accept("扉が閉まっている。"))
    }
    @Test fun doesNotReplaceDialogueWithTemporarilyMissingLines() {
        val tracker = DialogueTracker()
        val full = "The gate is locked. Find another way inside."
        tracker.accept(full)
        assertEquals(full, tracker.accept(full))
        repeat(3) { assertNull(tracker.accept("The gate is locked. Find another way")) }
        repeat(2) { assertNull(tracker.accept("The gate is locked.")) }
        assertNull(tracker.accept("The gate is locked! Find another way inside."))
    }
    @Test fun speculativeCandidateIsAvailableBeforeConfirmationButMissingLinesAreRejected() {
        val tracker = DialogueTracker()
        val full = "The gate is locked. Find another way inside."
        assertNull(tracker.accept(full))
        assertEquals(full, tracker.candidateText)
        assertEquals(full, tracker.accept(full))
        tracker.accept("The gate is locked.")
        assertNull(tracker.candidateText)
        tracker.accept("")
        assertNull(tracker.candidateText)
    }
    @Test fun briefOcrDropoutDoesNotResetTheSameDialogue() {
        val tracker = DialogueTracker()
        tracker.accept("Find Bonnie")
        assertEquals("Find Bonnie", tracker.accept("Find Bonnie"))
        repeat(3) { tracker.accept("") }
        assertFalse(tracker.isEmpty)
        repeat(2) { assertNull(tracker.accept("Find Bonnie")) }
    }
    @Test fun handlesTypewriterTextAndResetsAfterDialogueDisappears() {
        val tracker = DialogueTracker()
        assertNull(tracker.accept("The"))
        assertNull(tracker.accept("The gate"))
        assertNull(tracker.accept("The gate is locked."))
        assertEquals("The gate is locked.", tracker.accept("The  gate is locked."))
        repeat(6) { tracker.accept("") }
        assertTrue(tracker.isEmpty)
        assertNull(tracker.accept("The gate is locked."))
        assertEquals("The gate is locked.", tracker.accept("The gate is locked."))
        tracker.retry()
        assertEquals("The gate is locked.", tracker.accept("The gate is locked."))
    }
}
