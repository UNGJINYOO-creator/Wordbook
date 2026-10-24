package com.wordbook.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A reward the parent sets up, bought with stars. */
data class Reward(val name: String, val cost: Int)

/** A reward the child has claimed; the parent marks it used. */
data class Coupon(val name: String, val claimedAt: Long, val used: Boolean)

/** Levels and their titles; the stars needed grow with each level. */
object Levels {
    val titles = listOf("초보 단어꾼", "손맛 아는 단어꾼", "베테랑 단어꾼", "영웅 단어꾼", "전설의 강태공", "신화 속 단어신", "심해의 은둔 고수")
    val grades = listOf("일반", "고급", "희귀", "영웅", "전설", "신화", "비밀")
    val descriptions = listOf(
        "이제 막 단어를 낚기 시작한 풋내기",
        "기초 단어는 막힘없이 척척 낚아채는 단어꾼",
        "어려운 단어도 묵직하게 잘 낚는 숙련자",
        "웬만한 고난도 단어도 다 알아보는 고수",
        "어떤 단어가 나와도 한 손으로 낚아채는 마스터",
        "단어의 경지에 올라선 만렙 단어꾼",
        "깊은 바다에 숨은 단어까지 낚는, 아무도 정체를 모르는 고수",
    )

    /** Fish (the app's points, once called stars) needed to reach [level] (1-based): 0, 20, 60, 120, 200, … */
    fun starsFor(level: Int): Int = 10 * (level - 1) * level

    fun levelOf(stars: Int): Int {
        var level = 1
        while (stars >= starsFor(level + 1)) level++
        return level
    }

    private fun index(level: Int) = (level - 1).coerceIn(0, titles.lastIndex)
    fun title(level: Int): String = titles[index(level)]
    fun grade(level: Int): String = grades[index(level)]
    fun description(level: Int): String = descriptions[index(level)]

    /** Deck cover emojis that each level unlocks (level 1 has the basic set). */
    val unlocks: Map<Int, List<String>> = mapOf(
        2 to listOf("🦊", "🐱"),
        3 to listOf("🧭", "🗺️"),
        4 to listOf("🛡️", "⚔️"),
        5 to listOf("🎣", "🐟"),
        6 to listOf("🔮", "🪄"),
        7 to listOf("🐋", "💎"),
    )

    /** The level that unlocks [emoji], or 1 for a basic one. */
    fun levelFor(emoji: String): Int = unlocks.entries.firstOrNull { emoji in it.value }?.key ?: 1
}

/** Stars, attendance stamps, badges and reward coupons. Kept in SharedPreferences. */
class Game(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("game", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun day(daysAgo: Int = 0) = dayFormat.format(Date(System.currentTimeMillis() - daysAgo * 86_400_000L))

    /** Stars earned in total; the level comes from this, so spending stars never lowers it. */
    val earned: Int get() = prefs.getInt("earned", 0)
    val spent: Int get() = prefs.getInt("spent", 0)
    val stars: Int get() = earned - spent
    val level: Int get() = Levels.levelOf(earned)
    val badges: Int get() = prefs.getInt("badges", 0)

    /** Adds stars; returns the new level if this crossed into one, else null. */
    fun addStars(n: Int): Int? {
        val before = level
        prefs.edit().putInt("earned", earned + n).apply()
        return level.takeIf { it > before }
    }

    fun hasStamp(daysAgo: Int = 0): Boolean = prefs.getBoolean("stamp_${day(daysAgo)}", false)

    /** Consecutive stamped days, counting today if stamped, else from yesterday. */
    val stampStreak: Int
        get() {
            val start = if (hasStamp(0)) 0 else 1
            var n = 0
            while (hasStamp(start + n)) n++
            return n
        }

    /**
     * Stamps today once. Every 7 days in a row earns a 🏅 badge and 20 bonus stars.
     * Returns true when a new badge was earned.
     */
    fun stampToday(): Boolean {
        if (hasStamp(0)) return false
        prefs.edit().putBoolean("stamp_${day()}", true).apply()
        if (stampStreak % 7 == 0) {
            prefs.edit().putInt("badges", badges + 1).apply()
            addStars(20)
            return true
        }
        return false
    }

    var rewards: List<Reward>
        get() = runCatching {
            val a = JSONArray(prefs.getString("rewards", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Reward(o.getString("name"), o.getInt("cost")) } }
        }.getOrDefault(emptyList())
        set(v) = prefs.edit().putString(
            "rewards",
            JSONArray(v.map { JSONObject().put("name", it.name).put("cost", it.cost) }).toString(),
        ).apply()

    var coupons: List<Coupon>
        get() = runCatching {
            val a = JSONArray(prefs.getString("coupons", "[]"))
            (0 until a.length()).map {
                a.getJSONObject(it).let { o -> Coupon(o.getString("name"), o.getLong("at"), o.getBoolean("used")) }
            }
        }.getOrDefault(emptyList())
        private set(v) = prefs.edit().putString(
            "coupons",
            JSONArray(v.map { JSONObject().put("name", it.name).put("at", it.claimedAt).put("used", it.used) }).toString(),
        ).apply()

    /** Spends stars on a reward; false if there aren't enough. */
    fun claim(reward: Reward): Boolean {
        if (stars < reward.cost) return false
        prefs.edit().putInt("spent", spent + reward.cost).apply()
        coupons = listOf(Coupon(reward.name, System.currentTimeMillis(), false)) + coupons
        return true
    }

    fun markUsed(index: Int) {
        coupons = coupons.mapIndexed { i, c -> if (i == index) c.copy(used = true) else c }
    }
}
