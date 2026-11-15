package com.wordbook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GraduateTest {
    private val rules = Graduate.Rules(on = true, streak = 10, spell = 5)
    private val start = 1_800_000_000_000L
    private fun word() = Word(deckId = 1, text = "pet", meaning = "반려동물")

    @Test
    fun countsOncePerDay() {
        var w = word()
        repeat(5) { w = Graduate.record(w, true, true, rules, start + it * 60_000L) }
        assertEquals(1, w.streak)
        assertEquals(1, w.spellRight)
    }

    @Test
    fun graduatesAfterTenDaysWithFiveSpellings() {
        var w = word()
        for (d in 0 until 10) w = Graduate.record(w, true, fullSpelling = d < 4, rules = rules, now = start + d * Srs.DAY)
        assertFalse(w.graduated) // only 4 spellings
        w = Graduate.record(w, true, true, rules, start + 10 * Srs.DAY)
        assertTrue(w.graduated)
        assertTrue(w.dueAt >= start + 10 * Srs.DAY + Graduate.RARE_GAP)
    }

    @Test
    fun missEndsGraduation() {
        var w = Graduate.stretch(word().copy(graduated = true, streak = 12, spellRight = 6), start)
        w = Graduate.record(w, false, false, rules, start + Srs.DAY)
        assertFalse(w.graduated)
        assertEquals(0, w.streak)
        assertEquals(0L, w.intervalMs)
        assertTrue(w.dueAt <= start + Srs.DAY)
    }

    @Test
    fun offDoesNotGraduate() {
        var w = word()
        for (d in 0 until 12) w = Graduate.record(w, true, true, rules.copy(on = false), start + d * Srs.DAY)
        assertFalse(w.graduated)
        assertEquals(12, w.streak)
    }

    @Test
    fun thinKeepsAboutOneInTwenty() {
        val words = (1..2000L).map { word().copy(id = it, graduated = true) } + word().copy(id = 9999)
        val kept = Graduate.thin(words, rules, Random(1))
        assertTrue(kept.any { it.id == 9999L })
        assertTrue(kept.size in 60..160)
        assertEquals(listOf(9999L), Graduate.thin(words, rules.copy(hide = true)).map { it.id })
        assertEquals(words.size, Graduate.thin(words, rules.copy(on = false)).size)
    }
}
