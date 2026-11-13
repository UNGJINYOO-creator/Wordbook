package com.wordbook.app.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wordbook.app.MainActivity
import com.wordbook.app.R
import com.wordbook.app.ml.PosGuess
import com.wordbook.app.ui.PosOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 잠금화면 단어 카드: one word stays in the notification shade, so it shows on the lock screen each
 * time the phone is turned on. Its 몰라요 / 헷갈려요 / 외웠어요 buttons move the review schedule like a
 * 뜻 카드 swipe (SRS only, no stars or stats) and bring the next word, without unlocking.
 * The setting lives on this phone only (not in the synced "study" prefs), so a parent's phone
 * doesn't start showing cards too.
 */
object LockCard {
    // "lockcard2": the first channel was shown as a silent notification, which many lock screens hide.
    private const val CHANNEL = "lockcard2"
    private const val NOTIFY_ID = 7301
    const val ACTION_ANSWER = "com.wordbook.app.LOCKCARD_ANSWER"
    const val ACTION_REFRESH = "com.wordbook.app.LOCKCARD_REFRESH"
    const val EXTRA_WORD = "word"
    const val EXTRA_RESULT = "result"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("lockcard", Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("on", false)

    /** Keep the meaning folded: the collapsed card shows only the word, the meaning when opened. */
    fun hideMeaning(context: Context) = prefs(context).getBoolean("hide", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        if (on) refreshAsync(context) else stop(context)
    }

    fun setHideMeaning(context: Context, hide: Boolean) {
        prefs(context).edit().putBoolean("hide", hide).apply()
        refreshAsync(context)
    }

    /** Words answered on the lock screen today. */
    fun todayCount(context: Context): Int {
        val p = prefs(context)
        return if (p.getString("day", null) == LocalDate.now().toString()) p.getInt("count", 0) else 0
    }

    private fun countOne(context: Context) {
        val today = LocalDate.now().toString()
        prefs(context).edit().putString("day", today).putInt("count", todayCount(context) + 1).apply()
    }

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { refresh(app) }.onFailure { saveError(app, it) }
        }
    }

    fun saveError(context: Context, e: Throwable) {
        prefs(context).edit().putString("error", "${e.javaClass.simpleName}: ${e.message}".take(200)).apply()
    }

    /** Why the card can't show, in words for the settings screen; null when all is fine. */
    fun problem(context: Context): String? {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return "이 앱의 알림이 꺼져 있어요"
        val ch = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return "'잠금화면 단어 카드' 알림 종류가 꺼져 있어요"
        if (ch != null && ch.lockscreenVisibility == Notification.VISIBILITY_SECRET) return "'잠금화면 단어 카드'가 잠금화면에서 숨겨져 있어요"
        return prefs(context).getString("error", null)?.let { "오류: $it" }
    }

    /** Is the card in the notification shade right now? */
    fun showing(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == NOTIFY_ID }
    }.getOrDefault(false)

    /** Shows the card again (the same word until it is answered) and keeps the hourly alarm set. */
    suspend fun refresh(context: Context) {
        if (!enabled(context)) { stop(context); return }
        scheduleAlarm(context)
        val dao = AppDatabase.get(context).wordDao()
        val current = prefs(context).getLong("current", 0L).takeIf { it > 0 }?.let { dao.getWord(it) }
        val word = current?.takeIf { !it.excluded } ?: pick(context)
        show(context, word)
    }

    /** One of the lock-screen buttons: update the schedule, then show the next word. */
    suspend fun answer(context: Context, wordId: Long, result: Srs.Result) {
        val dao = AppDatabase.get(context).wordDao()
        val word = dao.getWord(wordId)
        if (word != null) {
            var updated = Srs.apply(word, result)
            // Like a card swipe, 외웠어요 is only a claim until a quiz answer on a later day confirms it.
            if (result == Srs.Result.EASY) {
                MasterCheck(context).add(word.id)
                updated = updated.copy(mastered = false)
            }
            dao.updateWord(updated)
            dao.touchDecks(listOf(word.deckId), System.currentTimeMillis())
            countOne(context)
            markShown(context, word.id)
        }
        prefs(context).edit().putLong("current", 0L).apply()
        if (enabled(context)) show(context, pick(context)) else stop(context)
    }

    /** Due reviews first (oldest first), then new words, then any word, skipping the last few shown. */
    private suspend fun pick(context: Context): Word? {
        val dao = AppDatabase.get(context).wordDao()
        val recent = recentIds(context)
        val due = dao.getDueWords(System.currentTimeMillis()).sortedBy { it.dueAt }
        due.firstOrNull { it.id !in recent }?.let { return it }
        dao.getNewWords(30).filter { it.id !in recent }.randomOrNull()?.let { return it }
        val all = dao.getAllWords().filter { !it.excluded }
        return all.filter { !it.mastered && it.id !in recent }.randomOrNull()
            ?: all.filter { it.id !in recent }.randomOrNull()
            ?: all.randomOrNull()
    }

    private fun recentIds(context: Context): Set<Long> =
        prefs(context).getString("recent", "")!!.split(",").mapNotNull { it.toLongOrNull() }.toSet()

    private fun markShown(context: Context, id: Long) {
        val list = (listOf(id) + prefs(context).getString("recent", "")!!.split(",").mapNotNull { it.toLongOrNull() })
            .distinct().take(8)
        prefs(context).edit().putString("recent", list.joinToString(",")).apply()
    }

    /** "[동사] 연결하다, 잇다 · [명사] …": a label before each meaning, as in the app. */
    fun meaningText(pos: String, meaning: String): String {
        val parts = meaning.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(meaning) }
        val guessing = pos in listOf("명", "동", "형", "부")
        var prev = ""
        return parts.mapIndexed { i, part ->
            val p = if (i == 0) pos else if (guessing) PosGuess.guess(part) else ""
            val label = PosOptions.firstOrNull { it.first == p }?.second
            val text = if (label != null && (i == 0 || p != prev)) "[$label] $part" else part
            prev = p
            text
        }.joinToString("  ·  ")
    }

    private fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("lockcard")
        val ch = NotificationChannel(CHANNEL, "잠금화면 단어 카드", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "폰을 켤 때마다 잠금화면에 단어 1개를 보여 줘요"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(ch)
    }

    private fun answerIntent(context: Context, wordId: Long, result: Srs.Result): PendingIntent =
        PendingIntent.getBroadcast(
            context, 100 + result.ordinal,
            Intent(context, LockCardReceiver::class.java).setAction(ACTION_ANSWER)
                .putExtra(EXTRA_WORD, wordId).putExtra(EXTRA_RESULT, result.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun show(context: Context, word: Word?) {
        channel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 7301, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val count = todayCount(context)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setSubText(if (count > 0) "잠금화면 오늘 ${count}개" else "잠금화면 단어")
        if (word == null) {
            builder.setContentTitle("단어장이 비어 있어요").setContentText("앱에서 단어를 추가하면 여기에 나와요")
        } else {
            prefs(context).edit().putLong("current", word.id).apply()
            val meaning = meaningText(word.pos, word.meaning)
            val example = word.example.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: ""
            builder.setContentTitle(word.text)
                .setContentText(if (hideMeaning(context)) "👀 아래로 펼치면 뜻이 보여요" else meaning)
                .setStyle(NotificationCompat.BigTextStyle().bigText(meaning + example).setBigContentTitle(word.text))
                .addAction(0, "몰라요", answerIntent(context, word.id, Srs.Result.WRONG))
                .addAction(0, "헷갈려요", answerIntent(context, word.id, Srs.Result.RIGHT))
                .addAction(0, "외웠어요", answerIntent(context, word.id, Srs.Result.EASY))
        }
        runCatching { manager.notify(NOTIFY_ID, builder.build()) }
            .onSuccess { prefs(context).edit().remove("error").apply() }
            .onFailure { saveError(context, it) }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7302, Intent(context, LockCardReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Hourly, inexact: puts the card back if it was swiped away. No special alarm permission needed. */
    private fun scheduleAlarm(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setInexactRepeating(
            AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR,
            AlarmManager.INTERVAL_HOUR, alarmIntent(context),
        )
    }

    fun stop(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
    }
}

class LockCardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    LockCard.ACTION_ANSWER -> {
                        val id = intent.getLongExtra(LockCard.EXTRA_WORD, 0L)
                        val result = runCatching { Srs.Result.valueOf(intent.getStringExtra(LockCard.EXTRA_RESULT)!!) }.getOrNull()
                        if (id > 0 && result != null) LockCard.answer(app, id, result)
                    }
                    // Hourly alarm, phone restart, or app update.
                    else -> LockCard.refresh(app)
                }
            } catch (e: Exception) { LockCard.saveError(app, e)
            } finally {
                pending.finish()
            }
        }
    }
}
