package com.wordbook.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteImportTest {
    private fun w(text: String, meaning: String, pos: String) = PasteImport.PastedWord(text, meaning, pos)

    @Test
    fun manySeparators() {
        assertEquals(w("connect", "연결하다", "동"), PasteImport.parseLine("connect 연결하다"))
        assertEquals(w("connect", "연결하다", "동"), PasteImport.parseLine("connect - 연결하다"))
        assertEquals(w("connect", "연결하다", "동"), PasteImport.parseLine("1. connect: 연결하다"))
        assertEquals(w("connect", "연결하다", "동"), PasteImport.parseLine("connect\t연결하다"))
        assertEquals(w("helpful", "도움이 되는", "형"), PasteImport.parseLine("helpful = 도움이 되는"))
        assertEquals(w("connect", "연결하다", "동"), PasteImport.parseLine("연결하다 - connect"))
        assertEquals(w("look forward to", "~을 기대하다", "동"), PasteImport.parseLine("look forward to ~을 기대하다"))
        assertEquals(w("apple", "", ""), PasteImport.parseLine("• apple"))
        assertNull(PasteImport.parseLine("사과"))
    }

    @Test
    fun partOfSpeechMarks() {
        assertEquals(w("connect", "연결하다 / 연결", "동"), PasteImport.parseLine("connect (동) 연결하다 (명) 연결"))
        assertEquals(w("connect", "연결하다 / 연결", "동"), PasteImport.parseLine("connect v. 연결하다 n. 연결"))
        assertEquals(w("connect", "연결하다", "동"), PasteImport.parseLine("connect v. 연결하다"))
        assertEquals(w("fast", "빠른 / 빨리", "형"), PasteImport.parseLine("fast [형] 빠른 [부] 빨리"))
        assertEquals(w("fast", "빠른", "형"), PasteImport.parseLine("fast\t형\t빠른"))
        assertEquals(w("fast", "빨리", "부"), PasteImport.parseLine("fast 부사: 빨리"))
        assertEquals(w("connect", "연결하다", "동"), PasteImport.parseLine("connect 동 연결하다"))
        // A one-syllable word inside the meaning is not a part-of-speech mark.
        assertEquals(w("global", "전 세계의", "형"), PasteImport.parseLine("global 전 세계의"))
    }

    @Test
    fun headers() {
        listOf("# 1과", "[DAY 3]", "Unit 2", "3일차", "동물 단어", "Day 12 - travel", "단어장2:").forEach {
            assertTrue(it, PasteImport.isHeader(it))
        }
        listOf("connect 연결하다", "unit 단위", "apple").forEach { assertFalse(it, PasteImport.isHeader(it)) }
        assertEquals("DAY 3", PasteImport.headerName("[DAY 3]"))
        assertEquals("1과", PasteImport.headerName("# 1과"))
    }

    @Test
    fun severalDecks() {
        val decks = PasteImport.parse(
            """
            apple 사과
            # 1과
            connect 연결하다
            helpful 도움이 되는

            [DAY 2]
            fast 빠른
            fast 빨리
            빈 단어장:
            # 1과
            try 노력하다
            """.trimIndent(),
        )
        assertEquals(listOf("붙여넣은 단어장", "1과", "DAY 2"), decks.map { it.name })
        assertEquals(listOf("connect", "helpful", "try"), decks[1].words.map { it.text })
        assertEquals(w("fast", "빠른 / 빨리", "형"), decks[2].words.single())
    }

    private val dict = Dictionary.parse(
        listOf(
            "connect\t동\t연결하다, 잇다\t",
            "helpful\t형\t도움이 되는, 유용한\t",
            "try\t동\t노력하다, ~해보다 / 시도\t",
        ),
        emptyList(),
    )
    private val words = setOf("connect", "helpful", "try", "receive", "apple", "zebra")
    private fun check(word: PasteImport.PastedWord) =
        PasteImport.check(word, dict, { it in words }, { if (it == "recieve") "receive" else null })

    @Test
    fun secondCheck() {
        assertEquals(PasteImport.Note.OK, check(w("connect", "연결하다", "동")).note)
        check(w("try", "", "")).let {
            assertEquals(PasteImport.Note.FROM_DICTIONARY, it.note)
            assertEquals(w("try", "노력하다, ~해보다 / 시도", "동"), it.word)
        }
        assertEquals(PasteImport.Note.TRANSLATE, check(w("zebra", "", "")).note)
        check(w("connect", "연결", "동")).let {
            assertEquals(PasteImport.Note.POS_FIXED, it.note)
            assertEquals("연결하다, 잇다", it.word.meaning)
        }
        check(w("helpful", "도움이 되는", "명")).let {
            assertEquals(PasteImport.Note.POS_FIXED, it.note)
            assertEquals("형", it.word.pos)
        }
        check(w("helpful", "사과", "명")).let {
            assertEquals(PasteImport.Note.DIFFERENT, it.note)
            assertEquals("도움이 되는, 유용한", it.offer!!.meaning)
            assertEquals("사과", it.word.meaning)
        }
        assertEquals("receive", check(w("recieve", "받다", "동")).word.text)
        assertEquals(PasteImport.Note.SPELLING, check(w("qwzx", "받다", "동")).note)
        // Not in the dictionary: a verb label on a noun meaning follows the meaning.
        assertEquals("명", check(w("zebra", "얼룩말", "동")).word.pos)
    }
}
