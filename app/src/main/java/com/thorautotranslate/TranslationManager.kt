package com.thorautotranslate

import android.content.Context
import android.text.Html
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class TranslationMode { CLOUD, FREE, OFFLINE }

data class TranslationConfig(val source: String, val target: String, val mode: TranslationMode, val apiKey: String)

class TranslationManager(private val config: TranslationConfig, private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectionPool(connectionPool).dispatcher(Dispatcher())
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS).build()
    private val offline: Translator? = if (config.mode == TranslationMode.OFFLINE) Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(config.source).setTargetLanguage(config.target).build()
    ) else null
    // Start model preparation while the first game frame is being read.
    private var modelReady = offline?.downloadModelIfNeeded(DownloadConditions.Builder().build())
    private val cache = object : LinkedHashMap<String, String>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 128
    }

    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        if (config.source == config.target) return@withContext text
        synchronized(cache) { cache[text] }?.let { return@withContext it }
        val parts = mutableListOf<String>()
        for (chunk in translationChunks(text)) {
            parts += when (config.mode) {
                TranslationMode.OFFLINE -> {
                    val translator = checkNotNull(offline)
                    val preparation = modelReady ?: translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).also { modelReady = it }
                    try { preparation.await() }
                    catch (error: Exception) { modelReady = null; throw error }
                    translator.translate(chunk).await()
                }
                TranslationMode.CLOUD -> cloudTranslate(chunk)
                TranslationMode.FREE -> freeTranslate(chunk)
            }.trim()
        }
        if (parts.any { it.isBlank() }) throw IOException(context.getString(R.string.empty_response))
        val translated = parts.joinToString("\n")
        if (translated.isBlank()) throw IOException(context.getString(R.string.empty_response))
        synchronized(cache) { cache[text] = translated }
        translated
    }

    private suspend fun cloudTranslate(text: String): String {
        require(config.apiKey.isNotBlank()) { context.getString(R.string.key_required) }
        val payload = JSONObject().put("q", text).put("source", config.source)
            .put("target", config.target).put("format", "text").toString()
        val request = Request.Builder().url("https://translation.googleapis.com/language/translate/v2")
            .header("X-Goog-Api-Key", config.apiKey)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val body = execute(request)
        val value = JSONObject(body).getJSONObject("data").getJSONArray("translations")
            .getJSONObject(0).getString("translatedText")
        return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private suspend fun freeTranslate(text: String): String {
        // shortcut: unofficial Google endpoint can change or throttle; select Cloud or Offline if it fails.
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("translate.googleapis.com")
            .addPathSegments("translate_a/single").addQueryParameter("client", "gtx")
            .addQueryParameter("sl", config.source).addQueryParameter("tl", config.target)
            .addQueryParameter("dt", "t").addQueryParameter("q", text).build()
        val body = execute(Request.Builder().url(url).build())
        val parts = JSONArray(body).getJSONArray(0)
        return buildString {
            for (index in 0 until parts.length()) {
                parts.optJSONArray(index)?.optString(0)?.takeUnless { it == "null" }?.let(::append)
            }
        }
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation {
            // TLS cancellation can close a socket; never let it perform I/O on the service main thread.
            client.dispatcher.executorService.execute { call.cancel() }
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException(context.getString(R.string.connection_error), e))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) throw IOException(context.getString(R.string.http_error, it.code))
                        val body = it.body?.string().orEmpty()
                        if (continuation.isActive) continuation.resume(body)
                    } catch (error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    companion object {
        // Reuse TLS connections across game/settings changes; each session owns its request dispatcher.
        private val connectionPool = ConnectionPool()
    }

    fun close() {
        client.dispatcher.executorService.execute {
            client.dispatcher.cancelAll()
            offline?.close()
        }
    }
}
