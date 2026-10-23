package com.wordbook.app.data

import android.content.Context

/** How to study: one mode, or "단계별" which picks the mode from how well each word is known. */
enum class StudyMode(val label: String, val description: String) {
    STAGED("단계별 (추천)", "카드 → 4지선다 → 빈칸 → 듣고 쓰기 순서로 자동으로 어려워져요"),
    CARD("뜻 카드", "카드를 보고 뜻을 떠올리기"),
    CHOICE("4지선다", "뜻을 보고 단어 4개 중에서 고르기"),
    SPELLING("빈칸 스펠링", "몇 글자가 빠진 단어 채우기"),
    DICTATION("듣고 쓰기", "발음을 듣고 스펠링 쓰기"),
    MIXED("섞기", "여러 방식을 무작위로"),
}

enum class BlankLevel(val label: String) { AUTO("자동"), EASY("쉬움"), NORMAL("보통"), HARD("전부 쓰기") }

/** App-wide study settings, kept in SharedPreferences. */
class StudySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("study", Context.MODE_PRIVATE)

    var mode: StudyMode
        get() = runCatching { StudyMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(StudyMode.STAGED)
        set(v) = prefs.edit().putString("mode", v.name).apply()

    var blankLevel: BlankLevel
        get() = runCatching { BlankLevel.valueOf(prefs.getString("blank", null)!!) }.getOrDefault(BlankLevel.AUTO)
        set(v) = prefs.edit().putString("blank", v.name).apply()

    var autoSpeak: Boolean
        get() = prefs.getBoolean("autoSpeak", false)
        set(v) = prefs.edit().putBoolean("autoSpeak", v).apply()

    /** Maximum cards per session, 0 = no limit. */
    var sessionSize: Int
        get() = prefs.getInt("sessionSize", 0)
        set(v) = prefs.edit().putInt("sessionSize", v).apply()

    /** The study timer pauses after this many seconds without a touch. */
    var idleSeconds: Int
        get() = prefs.getInt("idleSeconds", 20)
        set(v) = prefs.edit().putInt("idleSeconds", v).apply()

    /** Minimum study time per day in minutes, 0 = no goal. */
    var dailyGoalMinutes: Int
        get() = prefs.getInt("dailyGoal", 0)
        set(v) = prefs.edit().putInt("dailyGoal", v).apply()

    var telegramToken: String
        get() = prefs.getString("tgToken", "") ?: ""
        set(v) = prefs.edit().putString("tgToken", v.trim()).apply()

    var telegramChatId: String
        get() = prefs.getString("tgChat", "") ?: ""
        set(v) = prefs.edit().putString("tgChat", v.trim()).apply()

    /** The first-launch guide has been shown. */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    /** Cards dealt into the "hand" at once; the rest wait until a card leaves the hand. */
    var handSize: Int
        get() = prefs.getInt("handSize", 20)
        set(v) = prefs.edit().putInt("handSize", v).apply()

    /** A card leaves the hand after this many 헷갈려요 (or one 외웠어요). */
    var knownToLeave: Int
        get() = prefs.getInt("knownToLeave", 3)
        set(v) = prefs.edit().putInt("knownToLeave", v).apply()

    /** Card order: shuffle each session instead of alphabetical. */
    var shuffle: Boolean
        get() = prefs.getBoolean("shuffle", true)
        set(v) = prefs.edit().putBoolean("shuffle", v).apply()

    /** Missed cards stay in the hand until known; off = every card is shown once. */
    var repeatMissed: Boolean
        get() = prefs.getBoolean("repeatMissed", true)
        set(v) = prefs.edit().putBoolean("repeatMissed", v).apply()

    /** Show the meaning first and recall the English word. */
    var backFirst: Boolean
        get() = prefs.getBoolean("backFirst", false)
        set(v) = prefs.edit().putBoolean("backFirst", v).apply()

    /** SHA-256 of the settings password, or "" when there is none. */
    var pinHash: String
        get() = prefs.getString("pinHash", "") ?: ""
        set(v) = prefs.edit().putString("pinHash", v).apply()

    val hasPin: Boolean get() = pinHash.isNotEmpty()

    fun setPin(pin: String?) {
        pinHash = pin?.let(::hash) ?: ""
    }

    fun checkPin(pin: String): Boolean = hash(pin) == pinHash

    private fun hash(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest("wordbook:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
