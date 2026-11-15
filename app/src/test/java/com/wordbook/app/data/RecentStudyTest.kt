package com.wordbook.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentStudyTest {
    @Test
    fun missedWordsComeFirstThenNewest() {
        val e = listOf(
            RecentStudy.Entry(1, time = 100, struggles = 0),
            RecentStudy.Entry(2, time = 300, struggles = 0),
            RecentStudy.Entry(3, time = 50, struggles = 2),
            RecentStudy.Entry(4, time = 200, struggles = 1),
            RecentStudy.Entry(5, time = 400, struggles = 2),
        )
        assertEquals(listOf(5L, 3L, 4L, 2L, 1L), RecentStudy.rank(e))
    }
}
