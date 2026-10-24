package com.wordbook.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Today's study totals (time, answers, missed words), reset each day. Kept in SharedPreferences. */
class DailyStats(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("daily", Context.MODE_PRIVATE)
    /** Per-day totals that are kept forever, for the streak and the stats screen. */
    private val history = context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun today() = dayFormat.format(Date())
    private fun daysAgo(n: Int) = dayFormat.format(Date(System.currentTimeMillis() - n * 86_400_000L))

    /** Clears the totals when the date has changed. */
    private fun ensureToday() {
        val t = today()
        if (prefs.getString("date", null) != t) {
            prefs.edit().clear().putString("date", t).apply()
        }
    }

    val studyMs: Long get() { ensureToday(); return prefs.getLong("ms", 0) }
    val answered: Int get() { ensureToday(); return prefs.getInt("answered", 0) }
    val right: Int get() { ensureToday(); return prefs.getInt("right", 0) }
    val missedWords: List<String>
        get() { ensureToday(); return prefs.getString("missed", "")!!.split('\n').filter { it.isNotBlank() } }
    /** Different words answered right in a quiz today (card swipes don't count). */
    val learnedCount: Int
        get() { ensureToday(); return learnedIds().size }

    private fun learnedIds(): Set<String> = prefs.getString("learned", "")!!.split(',').filter { it.isNotBlank() }.toSet()

    /** Counts [wordId] toward today's word goal; returns true the first time today. */
    fun addLearned(wordId: Long): Boolean {
        ensureToday()
        val ids = learnedIds()
        if (wordId.toString() in ids) return false
        prefs.edit().putString("learned", (ids + wordId.toString()).joinToString(",")).apply()
        val key = "words_${today()}"
        history.edit().putInt(key, history.getInt(key, 0) + 1).apply()
        return true
    }

    var wordGoalNotified: Boolean
        get() { ensureToday(); return prefs.getBoolean("wordsNotified", false) }
        set(v) { ensureToday(); prefs.edit().putBoolean("wordsNotified", v).apply() }

    var goalNotified: Boolean
        get() { ensureToday(); return prefs.getBoolean("notified", false) }
        set(v) { ensureToday(); prefs.edit().putBoolean("notified", v).apply() }

    fun addStudyMs(ms: Long) {
        ensureToday()
        prefs.edit().putLong("ms", prefs.getLong("ms", 0) + ms).apply()
        val key = "ms_${today()}"
        history.edit().putLong(key, history.getLong(key, 0) + ms).apply()
    }

    /** Study time per day for the last [n] days, oldest first. */
    fun lastDays(n: Int): List<Pair<String, Long>> =
        (n - 1 downTo 0).map { daysAgo(it).let { d -> d to history.getLong("ms_$d", 0) } }

    /** Answers and right answers over all days. */
    val totalAnswered: Int get() = history.getInt("answered", 0)
    val totalRight: Int get() = history.getInt("right", 0)

    /** Consecutive days with some study, counting today if studied, else from yesterday. */
    val streak: Int
        get() {
            val start = if (history.getLong("ms_${today()}", 0) > 0) 0 else 1
            var n = 0
            while (history.getLong("ms_${daysAgo(start + n)}", 0) > 0) n++
            return n
        }

    fun recordAnswer(word: String, correct: Boolean) {
        ensureToday()
        val e = prefs.edit()
            .putInt("answered", prefs.getInt("answered", 0) + 1)
            .putInt("right", prefs.getInt("right", 0) + if (correct) 1 else 0)
        if (!correct && word !in missedWords) {
            e.putString("missed", (missedWords + word).joinToString("\n"))
        }
        e.apply()
        history.edit()
            .putInt("answered", history.getInt("answered", 0) + 1)
            .putInt("right", history.getInt("right", 0) + if (correct) 1 else 0)
            .apply()
    }

    fun summary(goalMinutes: Int, goalWords: Int = 0): String {
        val total = studyMs / 1000
        val missed = missedWords
        return buildString {
            append("📚 단어장 오늘의 학습 완료!\n\n")
            append("⏱ 학습 시간: ${total / 60}분 ${total % 60}초")
            if (goalMinutes > 0) append(" (목표 ${goalMinutes}분)")
            append("\n")
            append("🧠 외운 단어: ${learnedCount}개")
            if (goalWords > 0) append(" (목표 ${goalWords}개)")
            append("\n")
            append("✏️ 푼 카드: ${answered}개 · 맞힘 ${right} · 틀림 ${answered - right}\n")
            if (answered > 0) append("🎯 정답률: ${right * 100 / answered}%\n")
            if (missed.isNotEmpty()) append("\n❗ 틀린 단어: ${missed.take(30).joinToString(", ")}")
        }
    }
}
