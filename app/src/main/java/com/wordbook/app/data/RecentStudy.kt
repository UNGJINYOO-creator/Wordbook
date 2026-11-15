package com.wordbook.app.data

import android.content.Context

/**
 * Words studied in the app lately (cards and quizzes), for the lock screen's '최근 공부한 단어'.
 * Kept per word: when it was last studied and how often it was missed or 헷갈려요 in that time.
 * Lock-screen answers are not noted here, so the lock screen keeps following what was studied in the app.
 */
object RecentStudy {
    private const val KEEP_DAYS = 7
    private const val MAX = 800

    data class Entry(val id: Long, val time: Long, val struggles: Int)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("recentStudy", Context.MODE_PRIVATE)

    private fun load(context: Context): MutableMap<Long, Entry> =
        prefs(context).getString("list", "")!!.split(";").mapNotNull { part ->
            val p = part.split(",")
            if (p.size < 3) null else runCatching { Entry(p[0].toLong(), p[1].toLong(), p[2].toInt()) }.getOrNull()
        }.associateBy { it.id }.toMutableMap()

    private fun save(context: Context, map: Map<Long, Entry>) {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * Srs.DAY
        val text = map.values.filter { it.time >= cutoff }.sortedByDescending { it.time }.take(MAX)
            .joinToString(";") { "${it.id},${it.time},${it.struggles}" }
        prefs(context).edit().putString("list", text).apply()
    }

    /** One answer in the app; [struggled] = 몰라요, a wrong answer, or 헷갈려요 on a card. */
    @Synchronized
    fun note(context: Context, wordId: Long, struggled: Boolean, now: Long = System.currentTimeMillis()) {
        val map = load(context)
        val old = map[wordId]
        map[wordId] = Entry(wordId, now, (old?.struggles ?: 0) + if (struggled) 1 else 0)
        save(context, map)
    }

    /**
     * Ids studied in the last [days] days, missed and 헷갈려요 words first (most missed first),
     * then the rest, newest first. Wrong quiz answers in [AnswerLog] (also fishing) count too.
     */
    fun ordered(context: Context, days: Int, now: Long = System.currentTimeMillis()): List<Long> {
        val cutoff = now - days * Srs.DAY
        val map = load(context).values.filter { it.time >= cutoff }.associateBy { it.id }.toMutableMap()
        val log = AnswerLog(context)
        for (d in 0 until days) {
            for (e in log.read(d)) {
                if (e.time < cutoff) continue
                val old = map[e.wordId]
                map[e.wordId] = Entry(
                    e.wordId,
                    maxOf(old?.time ?: 0L, e.time),
                    (old?.struggles ?: 0) + if (e.correct) 0 else 1,
                )
            }
        }
        return rank(map.values.toList())
    }

    /** Struggled words first (most first, then newest), then the others newest first. */
    fun rank(entries: List<Entry>): List<Long> {
        val (hard, easy) = entries.partition { it.struggles > 0 }
        return hard.sortedWith(compareByDescending<Entry> { it.struggles }.thenByDescending { it.time }).map { it.id } +
            easy.sortedByDescending { it.time }.map { it.id }
    }
}
