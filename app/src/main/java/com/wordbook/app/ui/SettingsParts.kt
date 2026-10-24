package com.wordbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.Game
import com.wordbook.app.data.Report
import com.wordbook.app.data.Reward
import com.wordbook.app.data.StudySettings
import com.wordbook.app.net.Telegram
import kotlinx.coroutines.launch

/**
 * A small number field that sits after a row of preset chips. It stays empty while a preset is chosen;
 * typing a number sets the value directly (clamped to [min]..[max]).
 */
@Composable
fun CustomNumber(current: Int, presets: List<Int>, suffix: String, max: Int, min: Int = 1, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current in presets) "" else current.toString()) }
    // A chip was tapped: clear the field unless it already shows that number.
    LaunchedEffect(current) { if (current in presets && text.toIntOrNull() != current) text = "" }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() }.take(max.toString().length)
            text.toIntOrNull()?.let { onSet(it.coerceIn(min, max)) }
        },
        placeholder = { Text("직접") },
        suffix = { Text(suffix) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(104.dp),
    )
}

/** A few preset chips plus a 직접 입력 field; wraps onto the next line instead of being cut off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumberChoice(
    current: Int,
    options: List<Pair<Int, String>>,
    suffix: String,
    max: Int,
    min: Int = 1,
    recommended: Int? = null,
    onSet: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        options.forEach { (n, label) ->
            FilterChip(
                selected = current == n,
                onClick = { onSet(n) },
                label = { Text(if (n == recommended) "⭐$label" else label) },
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        CustomNumber(current, options.map { it.first }, suffix, max, min, onSet)
    }
}

/** Chips for a fixed list of choices, wrapping when narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choice(current: T, options: List<Pair<T, String>>, recommended: T? = null, onSet: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = current == v, onClick = { onSet(v) }, label = { Text(if (v == recommended) "⭐$label" else label) })
        }
    }
}

/** A label above a choice row. */
@Composable
fun Labeled(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        Column(Modifier.padding(top = 4.dp)) { content() }
    }
}

@Composable
fun SwitchRow(title: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            desc?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Ready-made fishing difficulty; distances in metres (the line snaps at 200m). */
class FishLevel(val name: String, val start: Int, val right: Int, val wrong: Int, val drainStart: Int, val drainEnd: Int)

val FishLevels = listOf(
    FishLevel("쉬움", 80, 6, 2, 3, 8),
    FishLevel("보통", 100, 5, 3, 5, 12),
    FishLevel("어려움", 120, 4, 4, 7, 15),
)

fun StudySettings.applyFish(level: FishLevel) {
    // Stored as the old time values: distance = fishGoal - time.
    fishGoal = 200; fishStart = 200 - level.start
    fishRight = level.right; fishWrong = level.wrong
    fishDrainStart = level.drainStart; fishDrainEnd = level.drainEnd
}

fun StudySettings.fishLevel(): FishLevel? = FishLevels.firstOrNull {
    fishGoal == 200 && fishGoal - fishStart == it.start && fishRight == it.right && fishWrong == it.wrong &&
        fishDrainStart == it.drainStart && fishDrainEnd == it.drainEnd
}

/** The list of 보상 쿠폰 with a row to add one. */
@Composable
fun RewardEditor(game: Game, onChange: () -> Unit = {}) {
    var rewards by remember { mutableStateOf(game.rewards) }
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("50") }
    Column {
        rewards.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🎟 ${r.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("⭐ ${r.cost}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = {
                    rewards = rewards.filterIndexed { j, _ -> j != i }
                    game.rewards = rewards
                    onChange()
                }) { Text("삭제") }
            }
        }
        if (rewards.isEmpty()) {
            Text("아직 보상이 없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("보상 (예: 게임 30분)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = cost,
                onValueChange = { v -> cost = v.filter { it.isDigit() }.take(4) },
                label = { Text("별") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(76.dp),
            )
        }
        TextButton(
            onClick = {
                rewards = rewards + Reward(name.trim(), cost.toInt())
                game.rewards = rewards
                name = ""
                onChange()
            },
            enabled = name.isNotBlank() && (cost.toIntOrNull() ?: 0) > 0,
        ) { Text("＋ 보상 추가") }
        Text(
            "정답 1개에 ⭐1, 5콤보마다 ⭐2를 더 받아요. 하루에 보통 ⭐30~60개 정도 모여요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Bot token, two chat ids and the find / test buttons; every change is saved right away. */
@Composable
fun TelegramEditor(settings: StudySettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(settings.telegramToken) }
    var chatId by remember { mutableStateOf(settings.telegramChatId) }
    var status by remember { mutableStateOf<String?>(null) }
    Column {
        Text(
            "목표를 채우면 오늘의 학습 리포트를 텔레그램으로 보내요. 엄마·아빠 모두 받으려면 각자 봇에게 /start를 보낸 뒤 '채팅 ID 자동으로 찾기'를 누르세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; settings.telegramToken = it },
            label = { Text("봇 토큰 (BotFather가 준 값)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        // Two receivers (아빠, 엄마), saved together as "id1, id2".
        val ids = Telegram.splitIds(chatId)
        listOf("받는 사람 1 (예: 아빠) 채팅 ID", "받는 사람 2 (예: 엄마) 채팅 ID").forEachIndexed { i, label ->
            OutlinedTextField(
                value = ids.getOrElse(i) { "" },
                onValueChange = { v ->
                    val next = List(2) { j -> if (j == i) v.trim() else ids.getOrElse(j) { "" } }
                    chatId = next.filter { it.isNotEmpty() }.joinToString(", ")
                    settings.telegramChatId = chatId
                },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        TextButton(onClick = {
            scope.launch {
                status = "찾는 중…"
                Telegram.findChatIds(token)
                    .onSuccess { found ->
                        // Keep the ids already saved: Telegram only remembers the last day's messages.
                        val all = (Telegram.splitIds(chatId) + found).distinct().take(2)
                        chatId = all.joinToString(", ")
                        settings.telegramChatId = chatId
                        status = "${all.size}명에게 보낼게요"
                    }
                    .onFailure { status = it.message }
            }
        }, enabled = token.isNotBlank()) { Text("🔍 채팅 ID 자동으로 찾기") }
        TextButton(onClick = {
            scope.launch {
                status = "보내는 중…"
                val error = Telegram.send(token, chatId, "✅ 낚아보카와 연결됐어요! 목표를 채우면 여기로 결과를 보내드릴게요.")
                status = error ?: "테스트 메시지를 보냈어요. 텔레그램을 확인하세요"
            }
        }, enabled = token.isNotBlank() && chatId.isNotBlank()) { Text("📨 테스트 보내기") }
        TextButton(onClick = {
            scope.launch {
                status = "보내는 중…"
                val error = Telegram.send(token, chatId, Report.build(context))
                status = error ?: "오늘 학습 결과를 보냈어요"
            }
        }, enabled = token.isNotBlank() && chatId.isNotBlank()) { Text("📊 지금 오늘 학습 결과 보내기") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
}

/** Which extra Telegram messages the parents get. */
@Composable
fun ParentAlerts(settings: StudySettings) {
    var coupon by remember { mutableStateOf(settings.alertCoupon) }
    var streak by remember { mutableStateOf(settings.alertStreak) }
    var evening by remember { mutableStateOf(settings.alertEvening) }
    var backup by remember { mutableStateOf(settings.telegramBackup) }
    Column {
        SwitchRow("쿠폰을 받으면 알림", "아이가 별로 보상 쿠폰을 받는 순간", coupon) { coupon = it; settings.alertCoupon = it }
        SwitchRow("연속 학습이 끊기면 알림", "이어지던 공부를 하루 쉬면 다음 날", streak) { streak = it; settings.alertStreak = it }
        SwitchRow("저녁에 목표를 못 채웠으면 알림", "저녁 알림 시간에 남은 양을", evening) { evening = it; settings.alertEvening = it }
        SwitchRow("매주 자동 백업", "일주일에 한 번 백업 파일을 보내요", backup) { backup = it; settings.telegramBackup = it }
    }
}

/** The preset chips shared by the settings screen and the 설정 길잡이; everything else goes in 직접. */
object Opt {
    val dailyWords = listOf(0 to "끔", 10 to "10개", 20 to "20개", 30 to "30개")
    val minutes = listOf(0 to "끔", 10 to "10분", 20 to "20분", 30 to "30분")
    val reminder = listOf(-1 to "끔", 19 to "7시", 20 to "8시", 21 to "9시")
    val session = listOf(0 to "전부", 10 to "10장", 20 to "20장")
    val test = listOf(10 to "10문제", 20 to "20문제", 30 to "30문제")
    val hand = listOf(5 to "5장", 10 to "10장", 20 to "20장")
    val leave = listOf(1 to "1번", 2 to "2번", 3 to "3번")
    val recentShare = listOf(70 to "70%", 90 to "90%", 100 to "100%")
    val recentDays = listOf(3 to "3일", 7 to "7일", 14 to "14일")
    val idle = listOf(20 to "20초", 30 to "30초", 60 to "60초")
    val tries = listOf(0 to "무제한", 1 to "1번", 3 to "3번")
    val textScale = listOf(0.85f to "작게", 1f to "보통", 1.2f to "크게")

    fun reminderLabel(h: Int) = when {
        h < 0 -> "끔"
        h < 12 -> "오전 ${h}시"
        h == 12 -> "낮 12시"
        else -> "저녁 ${h - 12}시"
    }
}
