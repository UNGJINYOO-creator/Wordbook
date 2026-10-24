package com.wordbook.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelsTest {
    @Test
    fun levelsGrow() {
        assertEquals(1, Levels.levelOf(0))
        assertEquals(1, Levels.levelOf(19))
        assertEquals(2, Levels.levelOf(20))
        assertEquals(3, Levels.levelOf(60))
        assertEquals("베테랑 단어꾼", Levels.title(3))
        assertEquals("심해의 은둔 고수", Levels.title(20))
    }
}
