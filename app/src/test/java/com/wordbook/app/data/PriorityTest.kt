package com.wordbook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityTest {
    private val min = 60L * 1000
    private val builtIn = (1L..100L).map { Word(id = it, deckId = 1, text = "b$it", meaning = "", addedAt = 0) }

    private fun academy(id: Long, deck: Long = 2, at: Long = 1_000_000 + id * min) =
        Word(id = id, deckId = deck, text = "a$id", meaning = "", addedAt = at)

    private val decks = listOf(Deck(id = 1, name = "중학 영단어"), Deck(id = 2, name = "심슨단어장"), Deck(id = 3, name = "시험대비"))

    @Test
    fun allFiftyAcademyWordsComeEvenAboveTheDailyNumber() {
        val simpson = (1001L..1050L).map { academy(it) }
        val p = Priority(builtIn + simpson, decks, emptySet())
        val picked = Priority.todayNew(simpson.reversed() + builtIn, p::first, 30, 0, true, null)
        assertEquals(50, picked.size)
        assertTrue(picked.all { p.first(it) })
    }

    @Test
    fun fewAcademyWordsAreToppedUpWithBuiltIn() {
        val simpson = (1001L..1010L).map { academy(it) }
        val p = Priority(builtIn + simpson, decks, emptySet())
        val picked = Priority.todayNew(simpson + builtIn, p::first, 30, 0, true, null)
        assertEquals(30, picked.size)
        assertEquals(simpson, picked.take(10))
        val noFill = Priority.todayNew(simpson + builtIn, p::first, 30, 0, false, null)
        assertEquals(10, noFill.size)
    }

    @Test
    fun weakPartOfSpeechNeverPushesOutAcademyWords() {
        val simpson = (1001L..1020L).map { academy(it) }
        val verbs = builtIn.map { it.copy(pos = "동사") }
        val p = Priority(verbs + simpson, decks, emptySet())
        val picked = Priority.todayNew(simpson + verbs, p::first, 30, 0, true, "동사")
        assertTrue(picked.containsAll(simpson))
    }

    @Test
    fun switchedOffDeckIsLikeBuiltIn() {
        val exam = academy(2001, deck = 3)
        val p = Priority(builtIn + exam, decks, setOf("시험대비"))
        assertTrue(p.added(exam))
        assertFalse(p.first(exam))
        assertFalse(p.first(builtIn[0]))
    }

    @Test
    fun fishGroupsFollowTheWeights() {
        assertEquals(0, Priority.pickGroup(listOf(5, 5, 5), listOf(80, 20, 0), 0.5))
        assertEquals(1, Priority.pickGroup(listOf(5, 5, 5), listOf(80, 20, 0), 0.9))
        // No today's words: the 학원 words take every question.
        assertEquals(1, Priority.pickGroup(listOf(0, 5, 5), listOf(80, 20, 0), 0.1))
        assertNull(Priority.pickGroup(listOf(0, 0, 5), listOf(80, 20, 0), 0.1))
    }

    @Test
    fun oneTropicalFishPerAddedBatch() {
        val monday = (1L..50L).map { academy(it, at = 1_000_000 + it) }
        val friday = (51L..80L).map { academy(it, at = 900_000_000 + it) }
        val exam = (81L..90L).map { academy(it, deck = 3, at = 900_000_000 + it) }
        val groups = Priority.tropicalGroups(monday + friday + exam, TropicalMode.BATCH)
        assertEquals(listOf(50, 30, 10), groups.map { it.size })
        assertEquals(listOf(30, 30, 30), Priority.tropicalGroups(monday + friday + exam, TropicalMode.EVERY_30).map { it.size })
        assertEquals(listOf(80, 10), Priority.tropicalGroups(monday + friday + exam, TropicalMode.DECK).map { it.size })
    }
}
