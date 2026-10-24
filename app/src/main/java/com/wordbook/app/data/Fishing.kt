package com.wordbook.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A fish of the 단어 낚시 도전 game. Bigger fish come with higher levels. */
data class Fish(val level: Int, val emoji: String, val name: String, val minKg: Double, val maxKg: Double)

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
            prefs.edit().putString("date", today()).putInt("tries", 0).putBoolean("caught", false).apply()
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

    fun useTry() {
        ensureToday()
        prefs.edit().putInt("tries", prefs.getInt("tries", 0) + 1).apply()
    }

    /** Records a catch; returns true when it is the heaviest of its kind so far. */
    fun recordCatch(fish: Fish, kg: Double): Boolean {
        ensureToday()
        val best = prefs.getFloat("best_${fish.name}", 0f)
        prefs.edit()
            .putBoolean("caught", true)
            .putString("todayFish", "${fish.emoji} ${fish.name} ${formatKg(kg)}")
            .putInt("count_${fish.name}", count(fish) + 1)
            .putFloat("best_${fish.name}", maxOf(best, kg.toFloat()))
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

    fun count(fish: Fish): Int = prefs.getInt("count_${fish.name}", 0)
    fun best(fish: Fish): Double = prefs.getFloat("best_${fish.name}", 0f).toDouble()

    companion object {
        /** Spelling and dictation take longer to answer, so they give 2 seconds more. */
        const val TYPING_EXTRA = 2.0
        /** Every 3 right in a row reels in this much more. */
        const val COMBO_BONUS = 3.0
        const val CATCH_STARS = 10

        val species = listOf(
            Fish(1, "🐟", "붕어", 0.3, 1.2),
            Fish(2, "🐠", "잉어", 2.0, 9.0),
            Fish(3, "🐡", "대왕 복어", 3.0, 12.0),
            Fish(4, "🐟", "참치", 40.0, 220.0),
            Fish(5, "🦈", "백상아리", 300.0, 1100.0),
            Fish(6, "🐋", "혹등고래", 9000.0, 30000.0),
            Fish(7, "🦑", "심해 대왕오징어", 150.0, 500.0),
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
