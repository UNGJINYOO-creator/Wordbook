package com.wordbook.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One of our seas for the daily 단어 낚시 도전; the child picks one before each game. */
enum class Sea(val emoji: String, val title: String, val note: String) {
    WEST("🌅", "서해", "갯벌이 넓고 얕은 바다"),
    SOUTH("🏝", "남해", "섬이 많고 따뜻한 바다"),
    EAST("🌊", "동해", "깊고 차가운 바다"),
    JEJU("🌺", "제주 바다", "맑고 푸른 남쪽 바다"),
}

/**
 * A fish of the 단어 낚시 도전 game, living in [sea] (null for a giant). [rarity] 0..3 is 흔함·보통·희귀·전설:
 * a better game makes the rarer ones bite more often. [key] keeps the saved catches of a renamed fish.
 */
data class Fish(val sea: Sea?, val rarity: Int, val emoji: String, val name: String, val minKg: Double, val maxKg: Double, val key: String = name)

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

    /** The sea picked last time, offered again. */
    var sea: Sea
        get() = Sea.entries.firstOrNull { it.name == prefs.getString("sea", null) } ?: Sea.WEST
        set(v) { prefs.edit().putString("sea", v.name).apply() }

    /** Kinds of fish of [sea] caught at least once. */
    fun collected(sea: Sea): Int = species.count { it.sea == sea && count(it) > 0 }

    /** Marks [sea]'s book complete; true only the first time it is full. */
    fun completeSea(sea: Sea): Boolean {
        if (collected(sea) < species.count { it.sea == sea } || prefs.getBoolean("seaDone_${sea.name}", false)) return false
        prefs.edit().putBoolean("seaDone_${sea.name}", true).apply()
        return true
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

        /** Gift fish the first time a sea's whole book is filled. */
        const val SEA_GIFT = 30
        val rarityNames = listOf("흔함", "보통", "희귀", "전설")

        val species = listOf(
            Fish(Sea.WEST, 0, "🦀", "꽃게", 0.2, 0.5),
            Fish(Sea.WEST, 0, "🐙", "주꾸미", 0.05, 0.2),
            Fish(Sea.WEST, 1, "🐟", "우럭", 0.5, 2.0),
            Fish(Sea.WEST, 1, "🐟", "광어", 1.0, 5.0),
            Fish(Sea.WEST, 2, "🦑", "갑오징어", 0.3, 1.5),
            Fish(Sea.WEST, 3, "🐟", "민어", 5.0, 20.0),
            Fish(Sea.SOUTH, 0, "🐟", "고등어", 0.3, 1.0),
            Fish(Sea.SOUTH, 0, "🐟", "볼락", 0.1, 0.5),
            Fish(Sea.SOUTH, 1, "🐟", "갈치", 0.5, 3.0),
            Fish(Sea.SOUTH, 1, "🐡", "참복", 0.5, 2.0),
            Fish(Sea.SOUTH, 2, "🐟", "참돔", 1.0, 8.0),
            Fish(Sea.SOUTH, 3, "🐟", "감성돔", 2.0, 6.0),
            Fish(Sea.EAST, 0, "🦑", "오징어", 0.3, 1.0, key = "대왕 복어"),
            Fish(Sea.EAST, 0, "🐟", "도루묵", 0.1, 0.3),
            Fish(Sea.EAST, 1, "🦀", "대게", 0.5, 2.0),
            Fish(Sea.EAST, 1, "🐟", "임연수어", 0.3, 1.5),
            Fish(Sea.EAST, 2, "🐟", "방어", 5.0, 15.0, key = "참치"),
            Fish(Sea.EAST, 3, "🐟", "대구", 5.0, 20.0),
            Fish(Sea.JEJU, 0, "🐟", "자리돔", 0.05, 0.2),
            Fish(Sea.JEJU, 1, "🐟", "옥돔", 0.5, 2.0),
            Fish(Sea.JEJU, 1, "🐙", "문어", 1.0, 5.0),
            Fish(Sea.JEJU, 2, "🐟", "부시리", 5.0, 20.0),
            Fish(Sea.JEJU, 3, "🐟", "다금바리", 10.0, 50.0),
        )

        /**
         * The fish that bites in [sea] after a game of [quality] 0..1: common ones get rarer and
         * rare ones likelier the better the game, but any fish can come at any level.
         */
        fun pick(sea: Sea, quality: Double, random: Double = Math.random()): Fish {
            val q = quality.coerceIn(0.0, 1.0)
            val rarityWeight = doubleArrayOf(50 - 35 * q, 30.0, 15 + 20 * q, 5 + 25 * q * q)
            val fish = species.filter { it.sea == sea }
            val weights = fish.map { f -> rarityWeight[f.rarity] / fish.count { it.rarity == f.rarity } }
            var x = random * weights.sum()
            fish.forEachIndexed { i, f ->
                x -= weights[i]
                if (x < 0) return f
            }
            return fish.last()
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
