package com.wordbook.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wordbook.app.MainActivity
import com.wordbook.app.R
import com.wordbook.app.ui.LockWordActivity

/**
 * 잠금화면 큰 단어 화면 (B안): a small foreground service listens for the screen going off, and then
 * opens [LockWordActivity] above the lock screen, so the card is already there when the phone is
 * turned on. Opening a screen from the background needs the '다른 앱 위에 표시' permission.
 * Like [LockCard], the setting stays on this phone only.
 */
object LockScreen {
    private const val CHANNEL = "lockscreen_service"
    const val NOTIFY_ID = 7311

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("lockcard", Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("big", false)

    /** Cards per showing. */
    fun count(context: Context) = prefs(context).getInt("bigCount", 3)
    fun setCount(context: Context, n: Int) = prefs(context).edit().putInt("bigCount", n).apply()

    /** Minutes to wait before showing again; 0 = every time the screen is turned on. */
    fun gapMinutes(context: Context) = prefs(context).getInt("bigGap", 0)
    fun setGapMinutes(context: Context, m: Int) = prefs(context).edit().putInt("bigGap", m).apply()

    fun canOverlay(context: Context) = Settings.canDrawOverlays(context)

    fun ignoresBattery(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("big", on).apply()
        if (on) start(context) else context.stopService(Intent(context, LockScreenService::class.java))
    }

    /** Starts the listening service when switched on; safe to call often. */
    fun start(context: Context) {
        if (!enabled(context)) return
        runCatching { ContextCompat.startForegroundService(context, Intent(context, LockScreenService::class.java)) }
    }

    /** Called when the screen goes off: open the card screen unless it's too soon or a call is on. */
    fun onScreenOff(context: Context) {
        if (!enabled(context) || !canOverlay(context)) return
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return
        val gap = gapMinutes(context) * 60_000L
        val last = prefs(context).getLong("bigLast", 0L)
        if (gap > 0 && System.currentTimeMillis() - last < gap) return
        runCatching {
            context.startActivity(
                Intent(context, LockWordActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
            )
        }
    }

    /** The card screen was shown to the end or closed: start the waiting time from now. */
    fun markShown(context: Context) = prefs(context).edit().putLong("bigLast", System.currentTimeMillis()).apply()

    fun serviceNotification(context: Context): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "잠금화면 큰 단어 화면", NotificationManager.IMPORTANCE_MIN).apply {
                description = "화면을 켤 때 단어 화면을 띄우려고 켜 두는 알림이에요"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            context, 7312, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("잠금화면 단어 화면 켜짐")
            .setContentText("화면을 켜면 단어 카드가 먼저 나와요")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }
}

class LockScreenService : Service() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) LockScreen.onScreenOff(context.applicationContext)
        }
    }
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!LockScreen.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, LockScreen.NOTIFY_ID, LockScreen.serviceNotification(this), type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }
        if (!registered) {
            // Screen on/off can only be heard by a receiver registered in code, not in the manifest.
            registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            registered = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) runCatching { unregisterReceiver(receiver) }
        registered = false
        super.onDestroy()
    }
}
