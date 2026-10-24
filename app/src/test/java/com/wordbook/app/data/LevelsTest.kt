package com.wordbook.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelsTest {
    @Test
    fun levelsGrow() {
        assertEquals(1, Levels.levelOf(0))
        assertEquals(1, Levels.levelOf(29))
        assertEquals(2, Levels.levelOf(30))
        assertEquals(3, Levels.levelOf(90))
        assertEquals("단어 탐험가", Levels.title(3))
        assertEquals("단어 왕", Levels.title(20))
    }
}
