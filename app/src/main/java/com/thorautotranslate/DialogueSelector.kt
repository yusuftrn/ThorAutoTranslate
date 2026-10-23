package com.thorautotranslate

import java.util.Locale

/** Coordinates are normalized to the original game screen, including any OCR crop. */
data class OcrRegion(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

object DialogueSelector {
    private val spaces = Regex("\\s+")
    private val words = Regex("\\p{L}+(?:['’]\\p{L}+)*")
    private val telemetry = Regex("^(?:fps|frame(?:time|rate)?|cpu|gpu|ram|vram|ping|latency|speed|health|ammo|score|time)\\s*[:=]?\\s*[0-9.,]+(?:\\s*[%a-zA-Z/].*)?$", RegexOption.IGNORE_CASE)
    private val menu = Regex("^(?:new game|load game|save game|main menu|settings|options|graphics|audio|credits|multiplayer|return to title|continue game)$", RegexOption.IGNORE_CASE)
    private val instruction = Regex("\\b(?:press|hold|tap|push|release|find|follow|reach|return|meet|rescue|escape|defeat|protect|collect|pick up|take cover|move|ride|climb|jump|crouch|sprint|equip|interact|inspect|search|enter|leave|talk to|speak to|go to|head to|use|aim|shoot|reload|basın|basılı|dokun|bul|takip|ulaş|kullan|appuyez|maintenez|drücke|halten|pulsa|mantén)\\b", RegexOption.IGNORE_CASE)

    fun select(regions: List<OcrRegion>): String = regions.mapNotNull { region ->
        val text = region.text.lines().map { it.replace(spaces, " ").trim() }
            .filter { it.isNotBlank() && !telemetry.matches(it) }.joinToString(" ")
        val tokens = words.findAll(text).map { it.value }.toList()
        val letters = text.count(Char::isLetter)
        if (text.length < 3 || letters < text.length * .55f || menu.matches(text)) return@mapNotNull null
        val direction = tokens.size >= 2 && instruction.containsMatchIn(text)
        val cjk = text.count { it.code in 0x3040..0x30ff || it.code in 0x3400..0x9fff || it.code in 0xac00..0xd7af }
        val dialogueArea = region.bottom > .35f && region.left < .85f && region.right > .15f
        val sentence = text.any { it in ".!?。！？" } && (tokens.size >= 3 || cjk >= 4)
        val shortReply = dialogueArea && tokens.size in 1..2 &&
            (text.lastOrNull() in listOf('!', '?', '！', '？') ||
                (text.endsWith('.') && (tokens.any { it.first().isLowerCase() } || text.dropLast(1).lowercase(Locale.ROOT) in setOf("no", "yes", "thanks", "hello", "sorry"))))
        val prose = dialogueArea && tokens.size >= 5 && tokens.any { it.first().isLowerCase() }
        // shortcut: visual/text cues cannot perfectly infer narrative intent; calibrate profiles for unusual game layouts.
        if (direction || sentence || shortReply || prose) text else null
    }.distinctBy { it.lowercase(Locale.ROOT) }.joinToString("\n\n")
}
