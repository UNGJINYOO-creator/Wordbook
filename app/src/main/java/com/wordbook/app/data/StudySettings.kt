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
}
