package com.wordbook.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wordbook.app.data.LockCard
import kotlinx.coroutines.delay

/** Short summary for the settings group header. */
fun lockCardSummary(on: Boolean, hide: Boolean): String =
    if (!on) "꺼짐" else "켜짐 · " + if (hide) "뜻 가리기" else "뜻 바로 보기"

/** Rows of the 🔒 잠금화면 단어 group: on/off, hide the meaning, a status line, and the phone's own settings. */
@Composable
fun LockCardSettings(on: Boolean, hide: Boolean, onOn: (Boolean) -> Unit, onHide: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Bumped when coming back from the phone's settings or after showing the card, to re-check the status.
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                if (LockCard.enabled(context)) LockCard.refreshAsync(context)
                tick++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    val turnOn = {
        LockCard.setEnabled(context, true); onOn(true); tick++
        Toast.makeText(context, "잠금화면에 단어 카드를 띄웠어요. 화면을 껐다 켜 보세요", Toast.LENGTH_LONG).show()
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            turnOn()
        } else {
            // Denied before, so Android no longer shows the question: send to the app's notification settings.
            Toast.makeText(context, "알림을 '허용'으로 바꿔 주세요", Toast.LENGTH_LONG).show()
            LockCard.setEnabled(context, true); onOn(true)
            openSettings()
        }
    }
    // The status a moment after a change, once the card has had time to be posted.
    var problem by remember { mutableStateOf<String?>(null) }
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(on, hide, tick) {
        delay(600)
        problem = LockCard.problem(context)
        showing = LockCard.showing(context)
    }
    Column(Modifier.fillMaxWidth()) {
        SwitchRow(
            "잠금화면에 단어 카드 띄우기",
            "폰을 켤 때마다 단어 1개가 보여요. 몰라요·헷갈려요·외웠어요를 누르면 복습 일정에 들어가고 다음 단어로 바뀌어요",
            on,
        ) { want ->
            val allowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            when {
                !want -> { LockCard.setEnabled(context, false); onOn(false); tick++ }
                allowed -> turnOn()
                Build.VERSION.SDK_INT >= 33 -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> { LockCard.setEnabled(context, true); onOn(true); openSettings() }
            }
        }
        if (on) {
            SwitchRow("뜻 가려 두기", "처음엔 단어만 보이고, 카드를 아래로 펼치면 뜻이 보여요", hide) {
                LockCard.setHideMeaning(context, it); onHide(it)
            }
            val status = when {
                problem != null -> "⚠️ ${problem}. 아래 '알림 설정 열기'에서 켜 주세요."
                showing -> "✅ 지금 알림창에 단어 카드가 있어요 · 오늘 잠금화면에서 ${LockCard.todayCount(context)}개 공부"
                else -> "⚠️ 카드가 아직 안 보여요. 아래 '지금 다시 띄우기'를 눌러 보세요."
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (problem == null && showing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
            SecondaryButton(
                "🔄 지금 다시 띄우기",
                { LockCard.refreshAsync(context); tick++ },
                Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        Text(
            "알림창엔 있는데 잠금화면에만 안 보이면, 휴대폰 설정 → 잠금화면 → 알림에서\n" +
                "① 알림 켜기  ② 보기 스타일 '상세 정보' (삼성은 처음에 '아이콘만'인 경우가 많아요)\n" +
                "③ '내용 숨기기'는 끄기  를 확인해 주세요. 카드를 밀어서 지워도 1시간 안에 다시 나와요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp),
        )
        SecondaryButton("🔔 이 앱 알림 설정 열기", openSettings, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}
