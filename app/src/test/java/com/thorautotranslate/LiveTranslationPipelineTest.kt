package com.thorautotranslate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LiveTranslationPipelineTest {
    @Test fun preparesOnTheFirstReadButPublishesOnlyAfterConfirmation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val calls = mutableListOf<String>()
        val results = mutableListOf<String>()
        val pipeline = LiveTranslationPipeline(scope, true, { calls.add(it); "translated $it" },
            { _, _, result, _ -> results.add(result) }, { _, error -> throw error })
        try {
            pipeline.observe("The gate is locked.", false, 123)
            assertEquals(1, calls.size)
            assertTrue(results.isEmpty())
            pipeline.observe("The gate is locked.", true, 456)
            repeat(3) { pipeline.observe("The gate is locked.", false, 789) }
            assertEquals(listOf("translated The gate is locked."), results)
            assertEquals(1, calls.size)
        } finally { pipeline.close(); scope.cancel() }
    }
    @Test fun onlineRequestsWaitForConfirmation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val calls = mutableListOf<String>()
        val pipeline = LiveTranslationPipeline(scope, false, { calls.add(it); it }, { _, _, _, _ -> }, { _, error -> throw error })
        try {
            pipeline.observe("The gate", false, 0)
            pipeline.observe("The gate is locked.", false, 0)
            assertTrue(calls.isEmpty())
            pipeline.observe("The gate is locked.", true, 0)
            assertEquals(listOf("The gate is locked."), calls)
        } finally { pipeline.close(); scope.cancel() }
    }
    @Test fun aSlowTranslationCannotBuildAnUnboundedBacklog() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<String>()
        val calls = mutableListOf<String>()
        val results = mutableListOf<String>()
        val pipeline = LiveTranslationPipeline(scope, true, {
            calls.add(it)
            if (it == "First") gate.await() else it
        }, { _, source, _, _ -> results.add(source) }, { _, error -> throw error })
        try {
            pipeline.observe("First", true, 0)
            repeat(100) { pipeline.observe("Skipped $it", true, 0) }
            pipeline.observe("Newest", true, 0)
            assertEquals(listOf("First"), calls)
            gate.complete("First translated")
            assertEquals(listOf("First", "Newest"), calls)
            assertEquals(listOf("First", "Newest"), results)
        } finally { pipeline.close(); scope.cancel() }
    }
    @Test fun unconfirmedFragmentsAndCancelledSessionsNeverReachThePanel() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<String>()
        val results = mutableListOf<String>()
        val pipeline = LiveTranslationPipeline(scope, true, { gate.await() }, { _, _, result, _ -> results.add(result) }, { _, error -> throw error })
        try {
            pipeline.observe("An incomplete sentence", false, 0)
            pipeline.observe("The completed sentence.", true, 0)
            pipeline.close()
            gate.complete("late output")
            assertTrue(results.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun vanishedSpeculationIsDiscardedAndRepeatedDialogueCanBeShownAgain() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val results = mutableListOf<String>()
        val pipeline = LiveTranslationPipeline(scope, true, { it }, { _, _, result, _ -> results.add(result) }, { _, error -> throw error })
        try {
            pipeline.observe("First fragment", false, 0)
            pipeline.observe(null, false, 0)
            pipeline.observe("Come back!", true, 0)
            pipeline.observe(null, false, 0)
            pipeline.observe("Come back!", true, 0)
            assertEquals(listOf("Come back!", "Come back!"), results)
        } finally { pipeline.close(); scope.cancel() }
    }
    @Test fun transientErrorsCanBeRetriedWithoutKillingTheWorker() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var first = true
        var failed = 0L
        val results = mutableListOf<String>()
        val pipeline = LiveTranslationPipeline(scope, true, { if (first) { first = false; error("network") }; it },
            { _, _, result, _ -> results.add(result) }, { sequence, _ -> failed = sequence })
        try {
            pipeline.observe("Try again.", true, 0)
            assertTrue(failed > 0)
            pipeline.retry(failed)
            pipeline.observe("Try again.", true, 0)
            assertEquals(listOf("Try again."), results)
        } finally { pipeline.close(); scope.cancel() }
    }
}
