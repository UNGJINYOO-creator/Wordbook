package com.wordbook.app.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningCheckTest {
    @Test
    fun cleanMeaningsAreKept() {
        assertFalse(MeaningCheck.looksGarbled("인터넷 (= Net)"))
        assertFalse(MeaningCheck.looksGarbled("연결[접속]하다"))
        assertFalse(MeaningCheck.looksGarbled("구할[이용할] 수 있는; 시간이 있는"))
        assertFalse(MeaningCheck.looksGarbled("~와 연락하다"))
    }

    @Test
    fun brokenMeaningsAreReplaced() {
        assertTrue(MeaningCheck.looksGarbled("하hSalexamination 신체검사를 하"))
        assertTrue(MeaningCheck.looksGarbled(""))
        assertTrue(MeaningCheck.looksGarbled("®©"))
        assertTrue(MeaningCheck.looksGarbled("파 invent 동 발명하다"))
    }

    @Test
    fun disagreement() {
        assertFalse(MeaningCheck.disagree("발명(품)", "발명"))
        assertTrue(MeaningCheck.disagree("구할 수 있는", "가능한"))
    }
}
