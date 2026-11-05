package com.swaralab.domain.audio

/**
 * Lyric follow + on-device speech locale. Auto reads the script.
 * Latin songs (Spanish, French, …) need a manual pick — they share English letters.
 */
enum class LyricLanguage(
    val tag: String,
    val speechTag: String,
    val englishName: String,
    val nativeName: String = englishName,
    val usesAksharaTiming: Boolean = false,
) {
    AUTO("auto", "en-IN", "Auto"),
    ENGLISH("en", "en-IN", "English"),
    HINDI("hi", "hi-IN", "Hindi", "हिन्दी", true),
    TELUGU("te", "te-IN", "Telugu", "తెలుగు", true),
    TAMIL("ta", "ta-IN", "Tamil", "தமிழ்", true),
    KANNADA("kn", "kn-IN", "Kannada", "ಕನ್ನಡ", true),
    MALAYALAM("ml", "ml-IN", "Malayalam", "മലയാളം", true),
    BENGALI("bn", "bn-IN", "Bengali", "বাংলা", true),
    MARATHI("mr", "mr-IN", "Marathi", "मराठी", true),
    GUJARATI("gu", "gu-IN", "Gujarati", "ગુજરાતી", true),
    PUNJABI("pa", "pa-IN", "Punjabi", "ਪੰਜਾਬੀ", true),
    ODIA("or", "or-IN", "Odia", "ଓଡ଼ିଆ", true),
    URDU("ur", "ur-IN", "Urdu", "اردو", true),
    SINHALA("si", "si-LK", "Sinhala", "සිංහල", true),
    SPANISH("es", "es-ES", "Spanish", "Español"),
    PORTUGUESE("pt", "pt-BR", "Portuguese", "Português"),
    FRENCH("fr", "fr-FR", "French", "Français"),
    GERMAN("de", "de-DE", "German", "Deutsch"),
    ITALIAN("it", "it-IT", "Italian", "Italiano"),
    INDONESIAN("id", "id-ID", "Indonesian", "Indonesia"),
    MALAY("ms", "ms-MY", "Malay", "Melayu"),
    FILIPINO("fil", "fil-PH", "Filipino", "Filipino"),
    VIETNAMESE("vi", "vi-VN", "Vietnamese", "Tiếng Việt"),
    TURKISH("tr", "tr-TR", "Turkish", "Türkçe"),
    ARABIC("ar", "ar", "Arabic", "العربية", true),
    PERSIAN("fa", "fa-IR", "Persian", "فارسی", true),
    HEBREW("he", "he-IL", "Hebrew", "עברית", true),
    RUSSIAN("ru", "ru-RU", "Russian", "Русский"),
    UKRAINIAN("uk", "uk-UA", "Ukrainian", "Українська"),
    GREEK("el", "el-GR", "Greek", "Ελληνικά"),
    THAI("th", "th-TH", "Thai", "ไทย", true),
    CHINESE("zh", "zh-CN", "Chinese", "中文", true),
    JAPANESE("ja", "ja-JP", "Japanese", "日本語", true),
    KOREAN("ko", "ko-KR", "Korean", "한국어", true),
    ;

    val menuLabel: String
        get() = if (this == AUTO || nativeName == englishName) englishName else "$englishName · $nativeName"

    companion object {
        fun fromTag(tag: String?): LyricLanguage {
            if (tag.isNullOrBlank()) return AUTO
            return entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: AUTO
        }

        fun detect(text: String): LyricLanguage {
            if (text.isBlank()) return AUTO
            val counts = IntArray(entries.size)
            for (ch in text) {
                val lang = languageForChar(ch) ?: continue
                counts[lang.ordinal] += 1
            }
            val best = entries.maxBy { counts[it.ordinal] }
            return if (counts[best.ordinal] == 0) AUTO else best
        }

        fun resolve(stored: String?, lyrics: String): LyricLanguage {
            val chosen = fromTag(stored)
            return if (chosen == AUTO) detect(lyrics).let { if (it == AUTO) ENGLISH else it } else chosen
        }

        private fun languageForChar(ch: Char): LyricLanguage? {
            val code = ch.code
            return when (code) {
                in 0x0C00..0x0C7F -> TELUGU
                in 0x0B80..0x0BFF -> TAMIL
                in 0x0C80..0x0CFF -> KANNADA
                in 0x0D00..0x0D7F -> MALAYALAM
                in 0x0980..0x09FF -> BENGALI
                in 0x0A80..0x0AFF -> GUJARATI
                in 0x0A00..0x0A7F -> PUNJABI
                in 0x0B00..0x0B7F -> ODIA
                in 0x0D80..0x0DFF -> SINHALA
                in 0x0900..0x097F -> HINDI
                in 0xFB50..0xFDFF, in 0xFE70..0xFEFF -> ARABIC
                in 0x0600..0x06FF, in 0x0750..0x077F, in 0x08A0..0x08FF -> ARABIC
                in 0x0590..0x05FF -> HEBREW
                in 0x0400..0x04FF -> if (isUkrainianLetter(ch)) UKRAINIAN else RUSSIAN
                in 0x0370..0x03FF -> GREEK
                in 0x0E00..0x0E7F -> THAI
                in 0x3040..0x30FF, in 0x31F0..0x31FF -> JAPANESE
                in 0xAC00..0xD7AF, in 0x1100..0x11FF, in 0x3130..0x318F -> KOREAN
                in 0x4E00..0x9FFF -> CHINESE
                else -> if (ch.isLetter() && code < 0x80) ENGLISH else null
            }
        }

        private fun isUkrainianLetter(ch: Char): Boolean {
            return ch in "іїєґІЇЄҐ"
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
