package com.wordbook.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A fish of the 단어 낚시 도전 game. Bigger fish come with higher levels. */
/** [key] keeps the saved catches of a fish that was renamed. */
data class Fish(val level: Int, val emoji: String, val name: String, val minKg: Double, val maxKg: Double, val key: String = name)

/**
 * 단어 낚시 도전: the day's final boss. Opens once today's goal is met, tries a day as set (0 = no limit),
 * and a catch goes into the fish book (도감).
 */
class Fishing(context: Context) {
    private val settings = StudySettings(context)
    private val prefs = context.applicationContext.getSharedPreferences("fishing", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun today() = dayFormat.format(Date())

    private fun ensureToday() {
        if (prefs.getString("date", null) != today()) {
            prefs.edit().putString("date", today()).putInt("tries", 0).putBoolean("caught", false).remove("seen").apply()
        }
    }

    val unlimited: Boolean get() = settings.fishTries <= 0
    val triesLeft: Int
        get() {
            ensureToday()
            return if (unlimited) Int.MAX_VALUE else (settings.fishTries - prefs.getInt("tries", 0)).coerceAtLeast(0)
        }
    /** " (남은 도전 N번)", or nothing when there is no limit. */
    val triesNote: String get() = if (unlimited) "" else " (남은 도전 ${triesLeft}번)"
    val caughtToday: Boolean get() { ensureToday(); return prefs.getBoolean("caught", false) }
    val caughtFishToday: String get() { ensureToday(); return prefs.getString("todayFish", "") ?: "" }

    /** Words learned today after the last catch: the 🌊 새 바다 asks only these. */
    fun newSeaIds(learnedToday: Set<Long>): Set<Long> {
        ensureToday()
        if (!prefs.getBoolean("caught", false)) return emptySet()
        val seen = prefs.getStringSet("seen", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        return learnedToday - seen
    }

    /** After a catch, today's words so far no longer count as new for the 새 바다. */
    fun markSeen(learnedToday: Set<Long>) {
        prefs.edit().putStringSet("seen", learnedToday.map { it.toString() }.toSet()).apply()
    }

    fun useTry() {
        ensureToday()
        prefs.edit().putInt("tries", prefs.getInt("tries", 0) + 1).apply()
    }

    /** Records a catch; returns true when it is the heaviest of its kind so far. */
    fun recordCatch(fish: Fish, kg: Double): Boolean {
        ensureToday()
        val best = prefs.getFloat("best_${fish.key}", 0f)
        prefs.edit()
            .putBoolean("caught", true)
            .putString("todayFish", "${fish.emoji} ${fish.name} ${formatKg(kg)}")
            .putInt("count_${fish.key}", count(fish) + 1)
            .putFloat("best_${fish.key}", maxOf(best, kg.toFloat()))
            .apply()
        return kg > best
    }

    val start: Double get() = settings.fishStart.toDouble()
    val goal: Double get() = maxOf(settings.fishGoal, settings.fishStart + 10).toDouble()
    val right: Double get() = settings.fishRight.toDouble()
    val wrong: Double get() = settings.fishWrong.toDouble()

    /** How fast the line runs out: faster as the fish gets close, since it fights harder. */
    fun drainPerSecond(seconds: Double): Double {
        val from = settings.fishDrainStart / 10.0
        val to = settings.fishDrainEnd / 10.0
        return from + (to - from) * ((seconds - start) / (goal - start)).coerceIn(0.0, 1.0)
    }

    fun count(fish: Fish): Int = prefs.getInt("count_${fish.key}", 0)
    fun best(fish: Fish): Double = prefs.getFloat("best_${fish.key}", 0f).toDouble()

    companion object {
        /** Spelling and dictation take longer to answer, so they give 2 seconds more. */
        const val TYPING_EXTRA = 2.0
        /** Every 3 right in a row reels in this much more. */
        const val COMBO_BONUS = 3.0
        const val CATCH_STARS = 10
        /** Extra stars for a catch in the 새 바다 (new words only). */
        const val NEW_SEA_BONUS = 5
        /** The 새 바다 opens once this many words were learned after the last catch. */
        const val NEW_SEA_MIN = 4

        val species = listOf(
            Fish(1, "🐟", "붕어", 0.3, 1.2),
            Fish(2, "🎏", "잉어", 2.0, 9.0),
            Fish(3, "🦑", "오징어", 0.5, 2.5, key = "대왕 복어"),
            Fish(4, "🐟", "방어", 5.0, 15.0, key = "참치"),
            Fish(5, "🦈", "청새리상어", 50.0, 200.0, key = "백상아리"),
            Fish(6, "🐋", "혹등고래", 9000.0, 30000.0),
            Fish(7, "🐳", "북극고래", 50000.0, 100000.0, key = "심해 대왕오징어"),
        )

        /** The fish for a level: the biggest one unlocked, sometimes a smaller one for variety. */
        fun fishFor(level: Int): Fish {
            val open = species.filter { it.level <= level }
            return if (open.size > 1 && Math.random() < 0.25) open.dropLast(1).random() else open.last()
        }

        /** Weight from how well the game went: [quality] 0..1. */
        fun weight(fish: Fish, quality: Double): Double =
            fish.minKg + (fish.maxKg - fish.minKg) * quality.coerceIn(0.0, 1.0)

        fun formatKg(kg: Double): String = when {
            kg >= 1000 -> "%.1f톤".format(kg / 1000)
            kg >= 10 -> "${kg.toInt()}kg"
            else -> "%.1fkg".format(kg)
        }

    }
}
