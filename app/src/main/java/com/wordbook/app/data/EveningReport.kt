package com.wordbook.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wordbook.app.net.Telegram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The parents' one Telegram a day: at the set evening hour, a three-line summary on top, what is still
 * missing, the full study report, and the day's 낚시·원정·시험 results collected through the day.
 */
object EveningReport {
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun today() = dayFormat.format(Date())
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("digest", Context.MODE_PRIVATE)

    /** Keeps a 낚시·원정·시험 result for tonight's report instead of sending it right away. */
    fun add(context: Context, text: String) {
        val p = prefs(context)
        val key = "d_${today()}"
        val old = p.getString(key, null)
        p.edit().putString(key, if (old.isNullOrEmpty()) text else "$old\n\n$text").apply()
    }

    private fun collected(context: Context): String? = prefs(context).getString("d_${today()}", null)

    /** Schedules the next evening report, or cancels it when switched off. */
    fun schedule(context: Context) {
        val settings = StudySettings(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 1, Intent(context, EveningReportReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pending)
        if (settings.reportHour < 0) { pending.cancel(); return }
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.reportHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        alarm.setWindow(AlarmManager.RTC_WAKEUP, next, 10 * 60_000L, pending)
    }

    /** Sends tonight's report once; returns null on success or an error message. */
    suspend fun send(context: Context, force: Boolean = false): String? {
        val settings = StudySettings(context)
        if (settings.telegramToken.isBlank() || settings.telegramChatId.isBlank()) return "텔레그램이 아직 연결되지 않았어요"
        val p = prefs(context)
        if (force) return Telegram.send(settings.telegramToken, settings.telegramChatId, build(context))
        // With 기기 동기화 both devices wake up for the report: whichever comes first marks the day and
        // syncs that mark before sending, and brings in the other device's games and tests too.
        com.wordbook.app.sync.FamilySync.run(context)
        if (p.getString("sent", null) == today()) return null
        p.edit().putString("sent", today()).commit()
        com.wordbook.app.sync.FamilySync.run(context)
        val error = Telegram.send(settings.telegramToken, settings.telegramChatId, build(context))
        if (error != null) p.edit().remove("sent").commit()
        return error
    }

    suspend fun build(context: Context): String {
        val settings = StudySettings(context)
        val stats = DailyStats(context)
        val log = AnswerLog(context)
        val dao = AppDatabase.get(context).wordDao()
        val fishing = Fishing(context)
        val answers = log.read()
        val missing = Reminder.missing(context)
        val endOfTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
        val due = dao.countDue(endOfTomorrow)
        val newWords = settings.dailyWords.takeIf { it > 0 } ?: 10

        // 👍 the best of the day
        val good = buildList {
            val goalsSet = settings.dailyWords > 0 || settings.dailyGoalMinutes > 0
            if (goalsSet && missing == null) add("오늘 목표 달성")
            if (stats.learnedCount > 0) add("${stats.learnedCount}단어 외움")
            if (fishing.caughtToday) add("낚시에서 ${fishing.caughtFishToday} 낚음")
        }
        // ⚠️ the weakest point, only when it is clearly weak
        val worry = run {
            val posNames = mapOf("명" to "명사", "동" to "동사", "형" to "형용사", "부" to "부사")
            val weakPos = answers.filter { it.pos in posNames }.groupBy { it.pos }
                .filter { it.value.size >= 5 }
                .map { (pos, l) -> posNames.getValue(pos) to l.count { it.correct } * 100 / l.size }
                .filter { it.second < 75 }.minByOrNull { it.second }
            val hardest = answers.filter { !it.correct }.groupingBy { it.text }.eachCount()
                .filter { it.value >= 2 }.maxByOrNull { it.value }
            listOfNotNull(
                weakPos?.let { "${it.first} 정답률 ${it.second}%" },
                hardest?.let { "${it.key}를 ${it.value}번 틀림" },
            ).joinToString(", ").ifEmpty { "특별히 약한 부분은 없어요" }
        }

        return buildString {
            append("🌙 낚아보카 저녁 리포트 · ${SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(Date())}\n\n")
            if (missing != null) append("⏰ 아직 오늘 목표까지 $missing 남았어요\n\n")
            append("👍 ${good.joinToString(", ").ifEmpty { "오늘은 아직 공부 기록이 없어요" }}\n")
            append("⚠️ $worry\n")
            append("📅 내일: 복습 ${due}개 + 새 단어 ${newWords}개\n")
            append("\n━━━━━━━━━━\n\n")
            append(Report.build(context).substringAfter("\n\n"))
            collected(context)?.let { append("\n━━━━━━━━━━\n🎣 오늘의 낚시 · 원정 · 시험\n\n").append(it).append("\n") }
        }
    }

    /** A short line the moment every goal that is switched on is met. */
    suspend fun goalPing(context: Context) {
        val settings = StudySettings(context)
        if (!settings.alertGoal) return
        val stats = DailyStats(context)
        val parts = listOfNotNull(
            if (settings.dailyWords > 0) "${stats.learnedCount}단어" else null,
            if (settings.dailyGoalMinutes > 0) "${stats.studyMs / 60_000}분" else null,
        )
        ParentAlerts.send(context, "✅ 오늘 목표 달성! (${parts.joinToString(" · ")})")
    }
}

class EveningReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            EveningReport.schedule(context)
            return
        }
        EveningReport.schedule(context)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { EveningReport.send(context) }
            result.finish()
        }
    }
}
