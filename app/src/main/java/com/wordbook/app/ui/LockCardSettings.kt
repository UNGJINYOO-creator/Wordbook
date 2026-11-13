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
fun lockCardSummary(on: Boolean, hide: Boolean, big: Boolean = false): String {
    val parts = listOfNotNull(
        if (big) "큰 단어 화면" else null,
        if (on) "알림 카드" + if (hide) "(뜻 가리기)" else "" else null,
    )
    return if (parts.isEmpty()) "꺼짐" else parts.joinToString(" · ") + " 켜짐"
}

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
        BigLockScreenSettings(tick) { tick++ }
        Text(
            "🔔 알림 카드",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        SwitchRow(
            "잠금화면 알림에 단어 카드 띄우기",
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
            "알림창엔 있는데 잠금화면에만 안 보이면 (삼성 갤럭시)\n" +
                "① 휴대폰 설정 → 알림 → 잠금화면 알림 → 켜기\n" +
                "② 같은 화면에서 '알림 내용 숨기기'는 끄고, '무음 알림 표시'는 켜기 (있을 때만)\n" +
                "③ 보기 스타일이 '아이콘만'이면 '상세 정보'(또는 '카드')로\n" +
                "④ 아래 '단어 카드 알림 종류 열기' → '무음'이 아니라 '소리/알림'으로, 잠금화면 '표시'\n" +
                "메뉴 이름은 폰마다 조금 달라요. 설정 맨 위 🔍 검색에 '잠금화면 알림'을 쳐도 나와요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp),
        )
        SecondaryButton(
            "🔔 단어 카드 알림 종류 열기",
            {
                context.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, LockCard.CHANNEL)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        SecondaryButton("🔔 이 앱 알림 설정 열기", openSettings, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

/** 큰 단어 화면 (B안): on/off, how many cards, how often, and the two permissions it needs. */
@Composable
private fun BigLockScreenSettings(tick: Int, onChange: () -> Unit) {
    val context = LocalContext.current
    var big by remember { mutableStateOf(com.wordbook.app.data.LockScreen.enabled(context)) }
    var count by remember { mutableIntStateOf(com.wordbook.app.data.LockScreen.count(context)) }
    var gap by remember { mutableIntStateOf(com.wordbook.app.data.LockScreen.gapMinutes(context)) }
    // Re-read after coming back from the phone's settings (tick changes on resume).
    val overlay = remember(tick) { com.wordbook.app.data.LockScreen.canOverlay(context) }
    val battery = remember(tick) { com.wordbook.app.data.LockScreen.ignoresBattery(context) }
    val openOverlay = {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    Text("📱 큰 단어 화면", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    SwitchRow(
        "화면을 켜면 큰 단어 카드 먼저 보기",
        "잠금화면보다 먼저 단어가 나와요. 앱처럼 밀어서 공부하고, '닫고 폰 쓰기'를 누르면 원래 잠금화면으로 가요",
        big,
    ) { want ->
        big = want
        com.wordbook.app.data.LockScreen.setEnabled(context, want)
        if (want && !com.wordbook.app.data.LockScreen.canOverlay(context)) {
            Toast.makeText(context, "'낚아보카'를 찾아 '다른 앱 위에 표시'를 허용해 주세요", Toast.LENGTH_LONG).show()
            openOverlay()
        }
        onChange()
    }
    if (big) {
        Labeled("한 번에 볼 카드") {
            Choice(count, listOf(1 to "1장", 3 to "3장", 5 to "5장", 10 to "10장")) {
                count = it; com.wordbook.app.data.LockScreen.setCount(context, it)
            }
        }
        Labeled("얼마나 자주", "매번이면 화면을 켤 때마다 나와요") {
            Choice(gap, listOf(0 to "매번", 10 to "10분마다", 30 to "30분마다", 60 to "1시간마다")) {
                gap = it; com.wordbook.app.data.LockScreen.setGapMinutes(context, it)
            }
        }
        if (!overlay) {
            Text(
                "⚠️ '다른 앱 위에 표시' 권한이 없어서 아직 안 나와요",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp),
            )
            SecondaryButton("✅ 다른 앱 위에 표시 허용하기", openOverlay, Modifier.fillMaxWidth().padding(top = 6.dp))
        } else {
            Text(
                "✅ 준비 완료! 화면을 껐다 켜 보세요",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!battery) {
            Text(
                "가끔 안 나오면 배터리 절약 때문이에요. 아래 버튼에서 '허용'을 눌러 주세요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
            SecondaryButton(
                "🔋 배터리 절약에서 빼기",
                {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + context.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}
