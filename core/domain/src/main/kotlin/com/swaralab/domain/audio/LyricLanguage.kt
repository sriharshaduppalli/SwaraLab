package com.swaralab.domain.audio

enum class LyricLanguage(
    val tag: String,
    val speechTag: String,
    val englishName: String,
) {
    AUTO("auto", "en-IN", "Auto"),
    ENGLISH("en", "en-IN", "English"),
    HINDI("hi", "hi-IN", "Hindi"),
    TELUGU("te", "te-IN", "Telugu"),
    TAMIL("ta", "ta-IN", "Tamil"),
    KANNADA("kn", "kn-IN", "Kannada"),
    MALAYALAM("ml", "ml-IN", "Malayalam"),
    BENGALI("bn", "bn-IN", "Bengali"),
    MARATHI("mr", "mr-IN", "Marathi"),
    ;

    val usesAksharaTiming: Boolean
        get() = this == TELUGU || this == TAMIL || this == KANNADA ||
            this == MALAYALAM || this == HINDI || this == BENGALI || this == MARATHI

    companion object {
        fun fromTag(tag: String?): LyricLanguage {
            if (tag.isNullOrBlank()) return AUTO
            return entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: AUTO
        }

        fun detect(text: String): LyricLanguage {
            if (text.isBlank()) return AUTO
            val counts = IntArray(entries.size)
            for (ch in text) {
                val lang = when (ch.code) {
                    in 0x0C00..0x0C7F -> TELUGU
                    in 0x0B80..0x0BFF -> TAMIL
                    in 0x0C80..0x0CFF -> KANNADA
                    in 0x0D00..0x0D7F -> MALAYALAM
                    in 0x0980..0x09FF -> BENGALI
                    in 0x0900..0x097F -> HINDI
                    else -> if (ch.isLetter() && ch.code < 0x80) ENGLISH else null
                }
                if (lang != null) counts[lang.ordinal] += 1
            }
            val best = entries.maxBy { counts[it.ordinal] }
            return if (counts[best.ordinal] == 0) AUTO else best
        }

        fun resolve(stored: String?, lyrics: String): LyricLanguage {
            val chosen = fromTag(stored)
            return if (chosen == AUTO) detect(lyrics).let { if (it == AUTO) ENGLISH else it } else chosen
        }
    }
}

object LyricLineMatcher {
    fun normalize(text: String): String {
        return buildString(text.length) {
            for (ch in text.lowercase()) {
                if (ch.isLetterOrDigit()) append(ch)
            }
        }
    }

    fun tokenCount(text: String, language: LyricLanguage): Int {
        val letters = text.count { it.isLetter() }
        if (language.usesAksharaTiming) {
            return letters.coerceAtLeast(1)
        }
        val words = text.split(Regex("\\s+")).count { token -> token.any { it.isLetterOrDigit() } }
        return words.coerceAtLeast(1)
    }

    fun score(heard: String, line: String): Float {
        val a = normalize(heard)
        val b = normalize(line)
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (b.contains(a) || a.contains(b)) {
            return (2f * minOf(a.length, b.length).toFloat()) / (a.length + b.length)
        }
        val gramsA = grams(a)
        val gramsB = grams(b)
        if (gramsA.isEmpty() || gramsB.isEmpty()) return 0f
        val overlap = gramsA.count { it in gramsB }
        return overlap.toFloat() / (gramsA.size + gramsB.size - overlap).toFloat()
    }

    fun bestLine(heard: String, lines: List<String>, currentIndex: Int): Int? {
        if (heard.isBlank() || lines.isEmpty()) return null
        val from = (currentIndex - 1).coerceAtLeast(0)
        val to = (currentIndex + 2).coerceAtMost(lines.lastIndex)
        var bestIndex: Int? = null
        var bestScore = 0.28f
        for (index in from..to) {
            val value = score(heard, lines[index])
            if (value > bestScore) {
                bestScore = value
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun grams(text: String): Set<String> {
        if (text.length == 1) return setOf(text)
        return (0 until text.length - 1).mapTo(mutableSetOf()) { text.substring(it, it + 2) }
    }
}
