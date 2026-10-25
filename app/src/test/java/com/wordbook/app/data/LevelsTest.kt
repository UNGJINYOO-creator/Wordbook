package com.wordbook.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelsTest {
    @Test
    fun levelsFollowPercentKnown() {
        assertEquals(1, Levels.levelOf(Levels.percent(0, 1800)))
        assertEquals(1, Levels.levelOf(Levels.percent(179, 1800)))
        assertEquals(2, Levels.levelOf(Levels.percent(180, 1800)))
        assertEquals(6, Levels.levelOf(Levels.percent(1799, 1800)))
        assertEquals(7, Levels.levelOf(Levels.percent(1800, 1800)))
        assertEquals(1, Levels.levelOf(Levels.percent(0, 0)))
        assertEquals("베테랑 단어꾼", Levels.title(3))
        assertEquals("심해의 은둔 고수", Levels.title(7))
    }
}
