package com.thorautotranslate

object GamePolicy {
    private val emulators = listOf(
        "org.ppsspp.", "org.dolphinemu.", "com.github.stenzek.duckstation",
        "xyz.aethersx2.", "xyz.aethersx2", "com.retroarch", "com.seleuco.mame4droid",
        "me.magnum.melonds", "io.github.lime3ds.", "dev.eden.", "org.citra.",
        "org.yuzu.", "com.xiaoji.egggame", "emu.x360mobile.",
        "com.redphx.betterxc", "com.nvidia.geforcenow", "org.sudachi.", "org.eden."
    )
    fun isAutomatic(packageName: String, isGame: Boolean): Boolean =
        isGame || emulators.any { packageName == it || packageName.startsWith(if (it.endsWith('.')) it else "$it.") }
}

internal fun dialogueKey(text: String): String =
    text.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }

/** Confirm whole dialogue blocks; a missing line must never replace a complete translation. */
class DialogueTracker {
    private var candidate = ""
    private var hits = 0
    private var emitted = ""
    private var emptyFrames = 0
    var candidateText: String? = null
        private set
    fun accept(raw: String): String? {
        candidateText = null
        val text = raw.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) {
            candidate = ""
            hits = 0
            if (++emptyFrames >= 6) emitted = ""
            return null
        }
        emptyFrames = 0
        val normalized = dialogueKey(text)
        if (normalized == dialogueKey(candidate)) hits++ else { candidate = text; hits = 1 }
        if (emitted.isNotBlank() && dialogueKey(emitted).contains(normalized) && normalized.length >= 8 && normalized != dialogueKey(emitted)) return null
        candidateText = text
        if (hits < 2 || normalized == dialogueKey(emitted)) return null
        emitted = text
        return text
    }
    val isEmpty: Boolean get() = emptyFrames >= 6
    fun retry() { emitted = "" }
}

/** Keep screen order even when network responses complete out of order. */
class TranslationHistory {
    private data class Line(val sequence: Long, val source: String, val translated: String)
    private val completed = sortedMapOf<Long, Line>()
    fun add(sequence: Long, source: String, translated: String) {
        val key = dialogueKey(source)
        // A typewriter continuation replaces its earlier fragment instead of scrolling a second row.
        val same = completed.values.lastOrNull {
            it.source == key || (it.sequence == completed.lastKey() && minOf(it.source.length, key.length) >= 24 &&
                (it.source.startsWith(key) || key.startsWith(it.source)))
        }
        if (same != null && same.sequence > sequence) return
        if (same != null && same.source.length > key.length && same.source.startsWith(key)) return
        same?.let { completed.remove(it.sequence) }
        completed[sequence] = Line(sequence, key, translated)
        while (completed.size > 3) completed.remove(completed.firstKey())
    }
    val texts: List<String> get() = completed.values.map { it.translated }.fold(mutableListOf()) { result, text ->
        if (result.lastOrNull() != text) result.add(text)
        result
    }
    fun clear() { completed.clear() }
}

/** Settings opened from the panel remain usable until a different foreground game session starts. */
internal fun shouldEnterTranslation(setupComplete: Boolean, enabled: Boolean, gameActive: Boolean,
    activationId: Long, settingsActivation: Long?): Boolean =
    setupComplete && enabled && gameActive && (settingsActivation == null || settingsActivation != activationId)
