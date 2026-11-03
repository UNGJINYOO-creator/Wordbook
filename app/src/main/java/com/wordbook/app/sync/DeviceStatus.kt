package com.wordbook.app.sync

import android.content.Context
import com.wordbook.app.BuildConfig
import com.wordbook.app.data.Deck
import com.wordbook.app.data.Word
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 연동 상태 확인: every device in the family leaves a small "device card" on the server when it syncs
 * (name, build, when it last synced, its settings and a few progress numbers), so 설정 can line the
 * devices up and show what doesn't match.
 */
object DeviceStatus {
    /** One device's card, as it was when that device last synced. */
    data class Card(
        val id: String,
        val name: String,
        val tablet: Boolean,
        val build: Int,
        val syncedAt: Long,
        /** Setting key → shown value (secrets only as a short fingerprint). */
        val settings: Map<String, String>,
        /** Progress key (see [STATS]) → number. */
        val stats: Map<String, Long>,
        val hidden: Boolean = false,
    )

    /** The progress numbers compared between devices, in the order shown. */
    val STATS = listOf(
        "words" to "전체 단어",
        "known" to "외운 단어",
        "studied" to "공부한 단어",
        "stars" to "별",
        "fish" to "잡은 물고기",
        "todayMin" to "오늘 공부(분)",
    )

    /** Settings that are secrets: compared, never shown. */
    private val SECRET = setOf("tgToken", "tgChat", "pinHash", "patternHash")

    private val LABELS = mapOf(
        "mode" to "공부 방식", "blank" to "빈칸 난이도", "autoSpeak" to "자동 발음", "sessionSize" to "한 번에 공부할 카드 수",
        "guessTenths" to "찍기 판정 시간", "idleSeconds" to "공부 시간 멈춤(초)", "dailyWords" to "하루 단어 목표",
        "reminderHour" to "공부 알림 시각", "alertCoupon" to "쿠폰 알림", "alertStreak" to "연속 공부 끊김 알림",
        "reportHour" to "저녁 리포트 시각", "alertGoal" to "목표 달성 알림", "alertEvening" to "저녁 알림",
        "textScale" to "글자 크기", "telegramBackup" to "텔레그램 자동 백업", "comboSound" to "콤보 소리",
        "soundEffects" to "효과음", "testSize" to "시험 문제 수", "fishStart" to "낚시 시작 거리", "fishGoal" to "낚시 목표 거리",
        "fishRight" to "낚시 정답 +", "fishWrong" to "낚시 오답 −", "fishRightTyping" to "낚시 정답 +(쓰기)",
        "fishWrongTyping" to "낚시 오답 −(쓰기)", "fishCombo" to "릴 감기", "fishDrainStart" to "줄 풀림(처음)",
        "fishDrainEnd" to "줄 풀림(끝)", "fishTries" to "하루 낚시 도전 횟수", "giantMix" to "대왕물고기 문제 비율",
        "childName" to "아이 이름", "listCover" to "단어 목록 가리기", "dailyGoal" to "하루 공부 시간 목표(분)",
        "tgToken" to "텔레그램 봇 토큰", "tgChat" to "텔레그램 받는 사람", "onboarded" to "처음 안내 봄",
        "handSize" to "카드 몇 장씩 돌리기", "knownToLeave" to "카드가 빠지는 헷갈려요 횟수", "shuffle" to "카드 섞기",
        "repeatMissed" to "틀린 카드 다시", "backFirst" to "뜻 먼저 보기", "pinHash" to "잠금 비밀번호",
        "patternHash" to "잠금 패턴", "useBiometric" to "지문 잠금", "modes" to "공부 순서", "staged" to "단계별로 공부",
        "dictPass" to "듣고 쓰기 통과 방식", "recentShare" to "최근 단어 비율", "recentDays" to "최근 단어 기준(일)",
        "rewards" to "보상 목록",
    )

    fun label(key: String): String = LABELS[key] ?: "기타 설정 ($key)"

    private fun sha(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // The id and name live outside shared_prefs and files/, so a backup restored on another device
    // doesn't make two devices look like one.
    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, "device.json")

    private fun local(context: Context): JSONObject {
        val f = file(context)
        val j = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        if (!j.has("id")) {
            j.put("id", (1..12).map { "abcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString(""))
            runCatching { f.writeText(j.toString()) }
        }
        return j
    }

    fun id(context: Context): String = local(context).getString("id")

    fun isTablet(context: Context) = context.resources.configuration.smallestScreenWidthDp >= 600

    fun name(context: Context): String = local(context).optString("name").ifBlank {
        (if (isTablet(context)) "태블릿" else "폰") + " (" + android.os.Build.MODEL + ")"
    }

    fun setName(context: Context, name: String) {
        val j = local(context).put("name", name.trim().take(20))
        runCatching { file(context).writeText(j.toString()) }
    }

    /** This device's card right now. */
    fun card(context: Context, words: List<Word>, @Suppress("UNUSED_PARAMETER") decks: List<Deck>, syncedAt: Long): Card {
        val settings = linkedMapOf<String, String>()
        val study = PrefCodec.export(context, "study", emptyMap())
        study.keys().asSequence().sorted().forEach { k ->
            val v = study.getJSONArray(k).get(1).toString()
            settings[k] = if (k in SECRET) (if (v.isBlank()) "(없음)" else "#" + sha(v).take(6)) else v
        }
        val rewards = PrefCodec.export(context, "rewards", emptyMap()).optJSONArray("list")?.optString(1).orEmpty()
        if (rewards.isNotBlank()) settings["rewards"] = "#" + sha(rewards).take(6)

        val daily = context.applicationContext.getSharedPreferences("daily", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayMs = if (daily.getString("date", null) == today) daily.getLong("ms", 0) else 0
        val fishing = context.applicationContext.getSharedPreferences("fishing", Context.MODE_PRIVATE)
        val fish = fishing.all.entries.filter { it.key.startsWith("count_") }.sumOf { (it.value as? Int ?: 0).toLong() }
        val stats = mapOf(
            "words" to words.size.toLong(),
            "known" to words.count { it.known || it.mastered }.toLong(),
            "studied" to words.count { it.box > 0 || it.correctCount + it.wrongCount > 0 }.toLong(),
            "stars" to com.wordbook.app.data.Game(context).stars.toLong(),
            "fish" to fish,
            "todayMin" to todayMs / 60_000,
        )
        return Card(id(context), name(context), isTablet(context), BuildConfig.VERSION_CODE, syncedAt, settings, stats)
    }

    /** The Firestore document for [card]. */
    fun toDoc(card: Card, now: Long): Map<String, Any?> = mapOf(
        "name" to card.name, "tablet" to card.tablet, "build" to card.build, "syncedAt" to card.syncedAt,
        "settings" to JSONObject(card.settings as Map<*, *>).toString(),
        "stats" to JSONObject(card.stats as Map<*, *>).toString(),
        "hidden" to false, "updatedAt" to now,
    )

    fun fromDoc(id: String, d: Map<String, Any?>): Card? {
        if (d["hidden"] == true) return Card(id, d["name"] as? String ?: "", false, 0, 0, emptyMap(), emptyMap(), hidden = true)
        val settings = runCatching { JSONObject(d["settings"] as String) }.getOrNull() ?: return null
        val stats = runCatching { JSONObject(d["stats"] as String) }.getOrNull() ?: JSONObject()
        val syncedAt = (d["syncedAt"] as? Long) ?: 0
        // 오늘 공부 from a device that last synced on another day is that day's time, not today's.
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        if (day.format(Date(syncedAt)) != day.format(Date())) stats.put("todayMin", 0)
        return Card(
            id = id,
            name = d["name"] as? String ?: "기기",
            tablet = d["tablet"] == true,
            build = ((d["build"] as? Long) ?: 0).toInt(),
            syncedAt = syncedAt,
            settings = settings.keys().asSequence().associateWith { settings.getString(it) },
            stats = stats.keys().asSequence().associateWith { stats.optLong(it) },
        )
    }

    // ---- What the 설정 screen shows ----

    /** Cards worth comparing: not hidden, synced within 30 days. */
    fun active(cards: List<Card>, now: Long = System.currentTimeMillis()): List<Card> =
        cards.filter { !it.hidden && now - it.syncedAt < 30L * 86_400_000 }

    /** Settings whose value isn't the same on every card: key → value per card (in card order). */
    fun settingDiffs(cards: List<Card>): List<Pair<String, List<String>>> {
        if (cards.size < 2) return emptyList()
        val keys = cards.flatMap { it.settings.keys }.toSortedSet()
        return keys.mapNotNull { k ->
            val values = cards.map { it.settings[k] ?: "(없음)" }
            if (values.toSet().size > 1) k to values else null
        }
    }

    /** Progress numbers that differ between the cards. */
    fun statDiffs(cards: List<Card>): Set<String> =
        if (cards.size < 2) emptySet() else STATS.map { it.first }.filter { k -> cards.map { it.stats[k] ?: 0 }.toSet().size > 1 }.toSet()

    fun newestBuild(cards: List<Card>): Int = cards.maxOfOrNull { it.build } ?: 0

    /** Something a parent should look at: an older build, or settings that don't match. */
    fun needsAttention(cards: List<Card>): Boolean {
        val a = active(cards)
        return a.size >= 2 && (a.map { it.build }.toSet().size > 1 || settingDiffs(a).isNotEmpty())
    }

    /** One line for the folded card. */
    fun summary(cards: List<Card>): String {
        val a = active(cards)
        if (a.size < 2) return "아직 다른 기기 정보가 없어요"
        val newest = newestBuild(a)
        val old = a.count { it.build < newest }
        val settings = settingDiffs(a).size
        val stats = statDiffs(a).size
        val parts = buildList {
            if (old > 0) add("업데이트 필요 ${old}대")
            if (settings > 0) add("설정 ${settings}개 다름")
            if (stats > 0) add("진도 ${stats}개 다름")
        }
        return if (parts.isEmpty()) "${a.size}대 모두 일치 ✅" else "⚠️ " + parts.joinToString(" · ")
    }

    // ---- Local cache of the family's cards, refreshed after each sync ----

    private fun cache(context: Context) = context.applicationContext.getSharedPreferences("syncDevices", Context.MODE_PRIVATE)


    fun save(context: Context, docs: Map<String, Map<String, Any?>>) {
        val j = JSONObject()
        docs.forEach { (id, d) -> j.put(id, JSONObject(d.mapValues { it.value ?: JSONObject.NULL })) }
        cache(context).edit().putString("cards", j.toString()).apply()
        refreshDot(context)
    }

    /** Every card in the family, this device's own one built fresh. */
    fun load(context: Context, mine: Card?): List<Card> {
        val j = runCatching { JSONObject(cache(context).getString("cards", "{}")!!) }.getOrDefault(JSONObject())
        val myId = id(context)
        val others = j.keys().asSequence().filter { it != myId }.mapNotNull { id ->
            val o = j.getJSONObject(id)
            fromDoc(id, o.keys().asSequence().associateWith { k -> o.get(k).takeIf { it != JSONObject.NULL }?.let { if (it is Int) it.toLong() else it } })
        }.sortedBy { it.name }.toList()
        val me = mine ?: j.optJSONObject(myId)?.let { o ->
            fromDoc(myId, o.keys().asSequence().associateWith { k -> o.get(k).takeIf { it != JSONObject.NULL }?.let { if (it is Int) it.toLong() else it } })
        }
        return listOfNotNull(me) + others
    }

    fun refreshDot(context: Context) {
        DeviceDot.attention.value = FamilySync.code(context) != null && needsAttention(load(context, null))
    }

    fun clear(context: Context) {
        cache(context).edit().clear().apply()
        DeviceDot.attention.value = false
    }
}

/** The red dot on the 설정 tab when the family's devices don't match (kept apart so the comparisons test on the JVM). */
object DeviceDot {
    val attention = androidx.compose.runtime.mutableStateOf(false)
}
