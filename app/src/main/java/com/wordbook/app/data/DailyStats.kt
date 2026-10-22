package com.wordbook.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Today's study totals (time, answers, missed words), reset each day. Kept in SharedPreferences. */
class DailyStats(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("daily", Context.MODE_PRIVATE)

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

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
    var goalNotified: Boolean
        get() { ensureToday(); return prefs.getBoolean("notified", false) }
        set(v) { ensureToday(); prefs.edit().putBoolean("notified", v).apply() }

    fun addStudyMs(ms: Long) {
        ensureToday()
        prefs.edit().putLong("ms", prefs.getLong("ms", 0) + ms).apply()
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
    }

    fun summary(goalMinutes: Int): String {
        val total = studyMs / 1000
        val missed = missedWords
        return buildString {
            append("📚 단어장 오늘의 학습 완료!\n\n")
            append("⏱ 학습 시간: ${total / 60}분 ${total % 60}초")
            if (goalMinutes > 0) append(" (목표 ${goalMinutes}분)")
            append("\n")
            append("✏️ 푼 카드: ${answered}개 · 맞힘 ${right} · 틀림 ${answered - right}\n")
            if (answered > 0) append("🎯 정답률: ${right * 100 / answered}%\n")
            if (missed.isNotEmpty()) append("\n❗ 틀린 단어: ${missed.take(30).joinToString(", ")}")
        }
    }
}
