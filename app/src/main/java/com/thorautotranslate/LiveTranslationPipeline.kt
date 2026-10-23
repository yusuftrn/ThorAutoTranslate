package com.thorautotranslate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** One inference in flight and one newest pending block; OCR confirmation can overlap inference. */
internal class LiveTranslationPipeline(
    scope: CoroutineScope,
    private val speculate: Boolean,
    private val translate: suspend (String) -> String,
    private val onResult: (Long, String, String, Long) -> Unit,
    private val onFailure: (Long, Exception) -> Unit
) {
    private class Request(val sequence: Long, val text: String, val seenAt: Long) {
        var confirmed = false
        var queued = false
        var delivered = false
        var result: String? = null
    }
    private val pending = Channel<Request>(Channel.CONFLATED)
    private var sequence = 0L
    private var observed: Request? = null
    private val worker: Job = scope.launch {
        for (request in pending) {
            if (request !== observed && !request.confirmed) continue
            try {
                request.result = translate(request.text)
                coroutineContext.ensureActive()
                deliver(request)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (request === observed) onFailure(request.sequence, error) }
        }
    }

    // Called on the service main thread, just like worker callbacks and OCR observations.
    fun observe(text: String?, confirmed: Boolean, seenAt: Long) {
        if (text == null) {
            observed = null
            return
        }
        val request = observed?.takeIf { dialogueKey(it.text) == dialogueKey(text) }
            ?: Request(++sequence, text, seenAt).also { observed = it }
        request.confirmed = request.confirmed || confirmed
        if (!request.queued && (speculate || request.confirmed)) {
            request.queued = true
            pending.trySend(request)
        }
        deliver(request)
    }

    private fun deliver(request: Request) {
        val result = request.result ?: return
        if (!request.confirmed || request.delivered) return
        request.delivered = true
        onResult(request.sequence, request.text, result, request.seenAt)
    }
    fun isLatest(sequence: Long) = observed?.sequence == sequence
    fun retry(sequence: Long) { if (isLatest(sequence)) observed = null }
    fun close() { observed = null; pending.close(); worker.cancel() }
}
