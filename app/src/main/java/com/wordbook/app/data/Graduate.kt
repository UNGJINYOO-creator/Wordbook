package com.wordbook.app.data

import android.content.Context
import java.util.TimeZone
import kotlin.random.Random

/**
 * 🎓 졸업 단어: a word answered right in quizzes [Rules.streak] days in a row (one count a day) that was
 * also spelled in full [Rules.spell] times graduates; so do the very easy words in assets/easy_words.txt,
 * at once. A graduated word comes up only rarely as a check (reviews at least [RARE_GAP] apart, about one
 * in [RARE_SHARE] in 오늘의 낚시 and on the lock screen) or, with [Rules.hide], not at all. One miss
 * sends it back to normal study with its count started over.
 */
object Graduate {
    const val RARE_GAP = 60 * Srs.DAY
    const val RARE_SHARE = 20

    data class Rules(val on: Boolean = true, val streak: Int = 10, val spell: Int = 5, val hide: Boolean = false)

    fun rules(settings: StudySettings) = Rules(settings.gradOn, settings.gradStreak, settings.gradSpell, settings.gradHide)
    fun rules(context: Context) = rules(StudySettings(context))

    /** Graduation in effect for this word (turned off in 설정 = none). */
    fun active(w: Word, rules: Rules) = rules.on && w.graduated

    fun day(now: Long): Int = ((now + TimeZone.getDefault().getOffset(now)) / Srs.DAY).toInt()

    /** A miss (in a quiz, or 몰라요 on a card or the lock screen): the run starts over and graduation ends. */
    fun miss(w: Word, now: Long = System.currentTimeMillis()): Word = w.copy(
        streak = 0, spellRight = 0, streakDay = 0, spellDay = 0, graduated = false,
        // A graduated word missed is back in normal study: soon again, not two months on.
        intervalMs = if (w.graduated) 0 else w.intervalMs,
        dueAt = if (w.graduated) minOf(w.dueAt, now) else w.dueAt,
    )

    /**
     * Counts one quiz answer. [fullSpelling]: the whole word was typed (듣고 쓰기, or 빈칸 with every letter blank).
     * Answers picked too fast to be read (찍기) shouldn't be passed in.
     */
    fun record(w: Word, correct: Boolean, fullSpelling: Boolean, rules: Rules, now: Long = System.currentTimeMillis()): Word {
        if (!correct) return miss(w, now)
        val today = day(now)
        var out = w
        if (out.streakDay != today) out = out.copy(streak = out.streak + 1, streakDay = today)
        if (fullSpelling && out.spellDay != today) out = out.copy(spellRight = out.spellRight + 1, spellDay = today)
        if (rules.on && !out.graduated && out.streak >= rules.streak && out.spellRight >= rules.spell) {
            out = stretch(out.copy(graduated = true), now)
        }
        return out
    }

    /** A graduated word's next review is at least two months away ([spread]: somewhere in the next two months). */
    fun stretch(w: Word, now: Long, spread: Boolean = false): Word {
        if (!w.graduated) return w
        val due = if (spread) now + Random.nextLong(7 * Srs.DAY, RARE_GAP) else now + RARE_GAP
        return w.copy(intervalMs = maxOf(w.intervalMs, RARE_GAP), dueAt = maxOf(w.dueAt, due), box = maxOf(w.box, 1), known = true)
    }

    /** For 오늘의 낚시 and the lock screen: graduated words thinned to about one in [RARE_SHARE] (none with hide). */
    fun thin(words: List<Word>, rules: Rules, random: Random = Random): List<Word> =
        if (!rules.on) words else words.filter { !it.graduated || (!rules.hide && random.nextInt(RARE_SHARE) == 0) }

    /** Due reviews: with hide, graduated words are left out. */
    fun reviews(words: List<Word>, rules: Rules): List<Word> =
        if (rules.on && rules.hide) words.filter { !it.graduated } else words

    @Volatile private var easy: Set<String>? = null

    fun easyWords(context: Context): Set<String> = easy ?: runCatching {
        context.applicationContext.assets.open("easy_words.txt").bufferedReader().readLines()
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet()).also { easy = it }

    /**
     * Once per device: the easy words graduate at once (their first checks spread over two months), and
     * the last 35 days of quiz answers are counted, so words already right many times in a row graduate now.
     */
    suspend fun seed(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("graduateSeed", Context.MODE_PRIVATE)
        if (prefs.getBoolean("done", false)) return
        val dao = AppDatabase.get(context).wordDao()
        val words = dao.getAllWords()
        if (words.isEmpty()) return
        val rules = rules(context)
        val now = System.currentTimeMillis()
        val log = AnswerLog(context)
        val byWord = (34 downTo 0).flatMap { log.read(it) }.sortedBy { it.time }.groupBy { it.wordId }
        val easyWords = easyWords(context)
        val changed = words.mapNotNull { w ->
            var out = w
            byWord[w.id]?.forEach { e ->
                val spelled = e.mode == StudyMode.DICTATION || e.mode == StudyMode.SPELLING
                out = record(out, e.correct, spelled, rules, e.time)
            }
            if (!out.graduated && w.addedAt == 0L && w.text.trim().lowercase() in easyWords) {
                out = stretch(out.copy(graduated = true), now, spread = true)
            }
            // Graduated from the log: the next check is two months after today, not after the old answer.
            if (out.graduated && !w.graduated) out = stretch(out, now, spread = true)
            out.takeIf { it != w }
        }
        if (changed.isNotEmpty()) dao.updateWords(changed)
        prefs.edit().putBoolean("done", true).apply()
    }
}
