package com.wordbook.app.data

/**
 * Spaced repetition (Leitner boxes). A right answer moves the word up a box and schedules the
 * next review further away; a wrong answer drops it back so it comes up again today.
 */
object Srs {
    private const val DAY = 24L * 60 * 60 * 1000
    /** Days until the next review for each box. */
    private val intervalDays = intArrayOf(0, 1, 3, 7, 14, 30, 60)
    const val MAX_BOX = 6

    enum class Result { WRONG, RIGHT, EASY }

    fun apply(word: Word, result: Result, now: Long = System.currentTimeMillis()): Word {
        val box = when (result) {
            Result.WRONG -> 1
            Result.RIGHT -> (word.box + 1).coerceAtMost(MAX_BOX)
            Result.EASY -> (word.box + 2).coerceAtMost(MAX_BOX)
        }
        val due = if (result == Result.WRONG) now else now + intervalDays[box] * DAY
        return word.copy(
            box = box,
            dueAt = due,
            known = result != Result.WRONG,
            mastered = when (result) { Result.EASY -> true; Result.WRONG -> false; Result.RIGHT -> word.mastered },
            correctCount = word.correctCount + if (result == Result.WRONG) 0 else 1,
            wrongCount = word.wrongCount + if (result == Result.WRONG) 1 else 0,
        )
    }
}
