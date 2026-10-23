package com.thorautotranslate

import org.junit.Assert.*
import org.junit.Test

class DialogueSelectorTest {
    private fun region(text: String, top: Float = .65f) = OcrRegion(text, .1f, top, .9f, top + .1f)
    @Test fun ignoresLogosMenusTelemetryAndNames() {
        val rejected = listOf("FPS: 30.0", "CPU: 45%", "120/500", "TOMB RAIDER", "TOMR", "MENU", "New game", "Graphics", "John Marston", "Ammo 15")
        rejected.forEach { assertEquals(it, "", DialogueSelector.select(listOf(region(it)))) }
    }
    @Test fun keepsDialogueAndPromptsIncludingUppercaseAndShortReplies() {
        listOf("The gate is locked.", "THE GATE IS LOCKED.", "Find Bonnie", "Press START to begin", "Time to leave this place.", "Speed up your horse by tapping B.", "Wait!", "No.", "Come on.", "扉が閉まっている。")
            .forEach { assertEquals(it, it, DialogueSelector.select(listOf(region(it)))) }
    }
    @Test fun keepsUpperCornerTutorialButDropsFpsInSameBlock() {
        val tutorial = "To spur your horse and move faster, tap B. If you hold B your horse will maintain its current speed."
        assertEquals(tutorial, DialogueSelector.select(listOf(region("FPS: 30.0\n$tutorial", .04f))))
    }
    @Test fun changingHudCannotEnterDialogueCandidate() {
        val tracker = DialogueTracker()
        fun frame(fps: Int) = DialogueSelector.select(listOf(region("FPS: $fps"), region("The gate is locked.")))
        assertNull(tracker.accept(frame(29)))
        assertEquals("The gate is locked.", tracker.accept(frame(30)))
        assertNull(tracker.accept(frame(31)))
    }
    @org.junit.Test fun longDialogueKeepsItsEnding() {
        val text = "Follow the river until you reach the old bridge. ".repeat(40) + "Meet Bonnie at the station."
        val selected = DialogueSelector.select(listOf(OcrRegion(text, .1f, .4f, .9f, .9f)))
        org.junit.Assert.assertEquals(text, selected)
    }
}
