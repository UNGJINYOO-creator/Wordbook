package com.wordbook.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.wordbook.app.data.LockCard

/** Short summary for the settings group header. */
fun lockCardSummary(on: Boolean, hide: Boolean): String =
    if (!on) "꺼짐" else "켜짐 · " + if (hide) "뜻 가리기" else "뜻 바로 보기"

/** Rows of the 🔒 잠금화면 단어 group: on/off, hide the meaning, and the phone's own settings. */
@Composable
fun LockCardSettings(on: Boolean, hide: Boolean, onOn: (Boolean) -> Unit, onHide: (Boolean) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        if (ok) { LockCard.setEnabled(context, true); onOn(true) }
    }
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    Column(Modifier.fillMaxWidth()) {
        SwitchRow(
            "잠금화면에 단어 카드 띄우기",
            "폰을 켤 때마다 단어 1개가 보여요. 몰라요·헷갈려요·외웠어요를 누르면 복습 일정에 들어가고 다음 단어로 바뀌어요",
            on,
        ) { want ->
            if (want && !allowed && Build.VERSION.SDK_INT >= 33) {
                ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                LockCard.setEnabled(context, want); onOn(want)
            }
        }
        if (on) {
            SwitchRow("뜻 가려 두기", "처음엔 단어만 보이고, 카드를 아래로 펼치면 뜻이 보여요", hide) {
                LockCard.setHideMeaning(context, it); onHide(it)
            }
            Text(
                "오늘 잠금화면에서 ${LockCard.todayCount(context)}개 공부했어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (on && !allowed) {
            Text(
                "알림이 꺼져 있어서 카드가 안 보여요. 아래 버튼에서 알림을 켜 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            "잠금화면에 단어가 안 보이면: 휴대폰 설정 → 잠금화면 → 알림 → '내용 표시'(또는 '모든 알림 내용 표시')를 켜 주세요. " +
                "카드를 밀어서 지워도 1시간 안에 다시 나와요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 6.dp),
        )
        SecondaryButton("🔔 이 앱 알림 설정 열기", openSettings, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}
