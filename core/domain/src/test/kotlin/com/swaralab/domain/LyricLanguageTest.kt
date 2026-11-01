package com.swaralab.domain

import com.swaralab.domain.audio.LyricLanguage
import com.swaralab.domain.audio.LyricLineMatcher
import com.swaralab.domain.audio.MusicalKeys
import com.swaralab.domain.model.ScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricLanguageTest {
    @Test
    fun detectsTeluguHindiAndEnglishScripts() {
        assertEquals(LyricLanguage.TELUGU, LyricLanguage.detect("\u0C1A\u0C41\u0C15\u0C4D\u0C15\u0C32\u0C4D\u0C32\u0C4B"))
        assertEquals(LyricLanguage.HINDI, LyricLanguage.detect("\u0924\u0941\u092E \u0939\u0940 \u0939\u094B"))
        assertEquals(LyricLanguage.ENGLISH, LyricLanguage.detect("Hold the final note"))
        assertEquals(LyricLanguage.TAMIL, LyricLanguage.detect("\u0B95\u0BBE\u0BA4\u0BB2\u0BCD"))
    }

    @Test
    fun matchesHeardFragmentToTheClosestLine() {
        val lines = listOf("pallavi line", "chukkalalo cheppukundam", "charanam")
        val index = LyricLineMatcher.bestLine("chukkalalo", lines, currentIndex = 0)
        assertEquals(1, index)
    }

    @Test
    fun mohanamScaleHasSixSwaraSteps() {
        val notes = MusicalKeys.scaleForKey("C", ScaleMode.MOHANAM)
        assertEquals(listOf("Sa", "Re", "Ga", "Pa", "Dha", "Sa'"), notes.map { it.swara })
        assertTrue(notes.first().hz > 250f)
    }
}
