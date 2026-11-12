package com.wordbook.app.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiMergeTest {
    @Test fun parsesJsonAnswerWithExtraText() {
        val answer = """```json
            [{"word":"Connect","pos":"동사","meaning":"연결하다, 잇다","from_book":true},
             {"word":"helpful","pos":"형","meaning":"도움이 되는"},
             {"word":"12","pos":"명","meaning":"x"},
             {"word":"connect","pos":"동","meaning":"dup"}]
            ```"""
        val words = GeminiVision.parse(answer)!!
        assertEquals(listOf("connect", "helpful"), words.map { it.word })
        assertEquals("동", words[0].pos)
        assertEquals("연결하다, 잇다", words[0].meaning)
    }

    @Test fun notJsonIsNull() {
        assertNull(GeminiVision.parse("sorry, I can't"))
    }

    @Test fun answerTextSkipsThoughts() {
        val resp = """{"candidates":[{"content":{"parts":[{"text":"thinking…","thought":true},{"text":"[]"}]}}]}"""
        assertEquals("[]", GeminiVision.answerText(resp))
    }

    @Test fun boxesComeFromTheBiggestMatchingLine() {
        val lines = listOf(
            OcrLine("I connect the cable.", 10, 300, 300, 320),
            OcrLine("connect 연결하다", 10, 100, 200, 140),
            OcrLine("give up 포기하다", 10, 200, 200, 240),
        )
        val merged = AiMerge.merge(
            listOf(AiWord("connect", "동", "연결하다"), AiWord("give up", "동", "포기하다"), AiWord("absent", "형", "결석한")),
            PairResult(emptyList(), emptyList()), lines,
        )
        assertEquals(Box(10, 100, 200, 140), merged[0].wordBox)
        assertEquals(Box(10, 200, 200, 240), merged[1].wordBox)
        assertNull(merged[2].wordBox)
    }
}
