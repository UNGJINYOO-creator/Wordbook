package com.wordbook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.sync.DeviceStatus
import com.wordbook.app.sync.FamilySync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 연동 상태 확인: the family's devices side by side — build, last sync, progress numbers, and any
 * setting that isn't the same everywhere. Folded to one line until tapped.
 * [refresh] changes after a sync so the cards are read again.
 */
@Composable
fun SyncStatusCard(refresh: Int, onSync: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cards by remember { mutableStateOf<List<DeviceStatus.Card>>(emptyList()) }
    var open by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var hiding by remember { mutableStateOf<DeviceStatus.Card?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(refresh, reload) {
        cards = withContext(Dispatchers.IO) {
            val dao = AppDatabase.get(context).wordDao()
            val mine = DeviceStatus.card(context, dao.getAllWords(), dao.getAllDecks(), FamilySync.lastSync(context))
            DeviceStatus.load(context, mine)
        }
        DeviceStatus.refreshDot(context)
    }
    val myId = remember { DeviceStatus.id(context) }
    val active = DeviceStatus.active(cards)
    val attention = active.size >= 2 && DeviceStatus.summary(cards).startsWith("⚠️")
    val time = SimpleDateFormat("M/d a h:mm", Locale.KOREA)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(
                if (attention) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }) {
            Column(Modifier.weight(1f)) {
                Text("🔍 연동 상태 확인", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(DeviceStatus.summary(cards), style = MaterialTheme.typography.bodySmall)
            }
            Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.outline)
        }
        if (!open) return@Column

        if (active.size < 2) {
            Text(
                "다른 기기가 아직 안 보여요. 모든 기기를 새 버전으로 업데이트하고 앱을 한 번씩 열면 여기에 나타나요.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // 1. Devices: name, build, last sync.
        val newest = DeviceStatus.newestBuild(active)
        Text("기기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        cards.filter { !it.hidden }.forEach { c ->
            val me = c.id == myId
            val stale = c !in active
            Column(Modifier.padding(top = 6.dp)) {
                Text(
                    (if (c.tablet) "💻 " else "📱 ") + c.name + if (me) " (이 기기)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "build-${c.build}" + (if (!stale && c.build < newest) "  ⚠️ 업데이트 필요" else "") +
                        " · 마지막 동기화 " + (if (c.syncedAt > 0) time.format(Date(c.syncedAt)) else "아직 없음") +
                        if (stale) " (30일 넘게 연결 안 됨)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!stale && c.build < newest) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (me) TextButton(onClick = { renaming = true }) { Text("이름 바꾸기") }
                    else TextButton(onClick = { hiding = c }) { Text("목록에서 빼기") }
                }
            }
        }

        if (active.size >= 2) {
            // 2. Progress, one column per device; rows that differ are red.
            val diffs = DeviceStatus.statDiffs(active)
            Text("진도", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("", Modifier.weight(1.4f))
                active.forEach { c ->
                    Text(
                        (if (c.tablet) "💻" else "📱") + c.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DeviceStatus.STATS.forEach { (key, label) ->
                val bad = key in diffs
                val color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text((if (bad) "⚠️ " else "") + label, Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall, color = color)
                    active.forEach { c ->
                        Text("${c.stats[key] ?: 0}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = color, fontWeight = if (bad) FontWeight.Bold else null)
                    }
                }
            }

            // 3. Settings that differ, by name.
            val settingDiffs = DeviceStatus.settingDiffs(active)
            Text("설정", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
            if (settingDiffs.isEmpty()) {
                Text("모든 설정이 같아요 ✅", style = MaterialTheme.typography.bodySmall)
            } else {
                settingDiffs.forEach { (key, values) ->
                    Text(
                        "⚠️ " + DeviceStatus.label(key) + ": " +
                            active.zip(values).joinToString(" / ") { (c, v) -> "${c.name} ${shown(v)}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            Text(
                "숫자는 각 기기가 마지막으로 동기화한 때 기준이에요. 다르면 그 기기에서 앱을 한 번 열었다 닫으면 맞춰져요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSync) { Text("🔄 지금 맞추고 다시 보기") }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(DeviceStatus.name(context)) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("이 기기 이름") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, singleLine = true, label = { Text("예: 형 폰, 엄마 폰, 탭") })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    DeviceStatus.setName(context, name)
                    renaming = false
                    onSync() // so the other devices see the new name
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("취소") } },
        )
    }
    hiding?.let { c ->
        AlertDialog(
            onDismissRequest = { hiding = null },
            title = { Text("${c.name} 빼기") },
            text = { Text("이제 안 쓰는 기기일 때만 빼세요. 그 기기에서 다시 동기화하면 목록에 다시 나타나요.") },
            confirmButton = {
                TextButton(onClick = {
                    hiding = null
                    scope.launch(Dispatchers.IO) {
                        FamilySync.hideDevice(context, c.id)
                        reload++
                    }
                }) { Text("빼기") }
            },
            dismissButton = { TextButton(onClick = { hiding = null }) { Text("취소") } },
        )
    }
}

private fun shown(v: String): String = when (v) {
    "true" -> "켜짐"
    "false" -> "꺼짐"
    "" -> "(비어 있음)"
    else -> if (v.startsWith("#")) "(값 숨김 $v)" else v
}
