package com.wordbook.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.BlankLevel
import com.wordbook.app.data.DictationPass
import com.wordbook.app.data.Game
import com.wordbook.app.data.Reward
import androidx.compose.foundation.layout.width
import com.wordbook.app.data.StudyMode
import com.wordbook.app.data.StudySettings
import com.wordbook.app.net.Telegram
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember { StudySettings(context) }
    var modes by remember { mutableStateOf(settings.modes) }
    var staged by remember { mutableStateOf(settings.staged) }
    var pass by remember { mutableStateOf(settings.dictationPass) }
    var blank by remember { mutableStateOf(settings.blankLevel) }
    var autoSpeak by remember { mutableStateOf(settings.autoSpeak) }
    var size by remember { mutableStateOf(settings.sessionSize) }
    var goal by remember { mutableStateOf(settings.dailyGoalMinutes) }
    var idle by remember { mutableStateOf(settings.idleSeconds) }
    var hand by remember { mutableStateOf(settings.handSize) }
    var recentShare by remember { mutableStateOf(settings.recentShare) }
    var recentDays by remember { mutableStateOf(settings.recentDays) }
    var leave by remember { mutableStateOf(settings.knownToLeave) }
    var token by remember { mutableStateOf(settings.telegramToken) }
    var chatId by remember { mutableStateOf(settings.telegramChatId) }
    var tgStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Settings apply immediately; a short toast confirms that the change was saved.
    val saved = { Toast.makeText(context, "저장됐어요", Toast.LENGTH_SHORT).show() }
    var shuffle by remember { mutableStateOf(settings.shuffle) }
    var repeatMissed by remember { mutableStateOf(settings.repeatMissed) }
    var backFirst by remember { mutableStateOf(settings.backFirst) }
    var hasPin by remember { mutableStateOf(settings.hasPin) }
    val game = remember { Game(context) }
    var rewards by remember { mutableStateOf(game.rewards) }
    var rewardName by remember { mutableStateOf("") }
    var rewardCost by remember { mutableStateOf("50") }
    var settingPin by remember { mutableStateOf(false) }
    // Locks again whenever the tab is left, since this state is forgotten then.
    var unlocked by remember { mutableStateOf(!settings.hasPin) }
    if (!unlocked) {
        PinLock(check = settings::checkPin, onUnlocked = { unlocked = true })
        return
    }
    if (settingPin) {
        NewPinDialog(
            onDismiss = { settingPin = false },
            onDone = { pin -> settings.setPin(pin); hasPin = true; settingPin = false; saved() },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppTopBar(
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("설정", style = MaterialTheme.typography.headlineMedium) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("학습 순서")
            Text(
                "켜 둔 단계가 위에서부터 순서대로 진행돼요. ▲▼로 순서를 바꿀 수 있어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ModePicker(modes, staged) { m, st ->
                modes = m; staged = st; settings.modes = m; settings.staged = st; saved()
            }
            if (StudyMode.DICTATION in modes) {
                Text(
                    "듣고 쓰기에서 '패스'를 누르면",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DictationPass.entries.forEach { p ->
                        FilterChip(
                            selected = pass == p,
                            onClick = { pass = p; settings.dictationPass = p; saved() },
                            label = { Text(p.label + " 빈칸으로") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text(
                    "소리가 꺼져 있으면 듣고 쓰기는 자동으로 빈칸 문제로 바뀌어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("빈칸 난이도")
            Text(
                "자동은 잘 외운 단어일수록 빈칸이 늘어나요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                BlankLevel.entries.forEach { b ->
                    FilterChip(
                        selected = blank == b,
                        onClick = { blank = b; settings.blankLevel = b; saved() },
                        label = { Text(b.label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("한 번에 학습할 카드 수")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(0 to "전부", 10 to "10", 20 to "20", 30 to "30", 50 to "50").forEach { (n, label) ->
                    FilterChip(
                        selected = size == n,
                        onClick = { size = n; settings.sessionSize = n; saved() },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("카드 순서")
            ToggleRow("카드 섞기", "학습할 때마다 순서를 무작위로 (끄면 알파벳 순)", shuffle) {
                shuffle = it; settings.shuffle = it; saved()
            }
            ToggleRow("틀린 카드 반복", "모르는 카드는 알 때까지 다시 나와요 (끄면 한 번씩만)", repeatMissed) {
                repeatMissed = it; settings.repeatMissed = it; saved()
            }
            ToggleRow("뒷면 먼저", "뜻을 먼저 보고 영어 단어 떠올리기", backFirst) {
                backFirst = it; settings.backFirst = it; saved()
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("종합단어장")
            Text(
                "종합단어장을 학습할 때 최근 넣은 단어를 얼마나 내고, 나머지는 기존 단어로 채워요. " +
                    "기존 단어는 복습할 때가 된 것과 자주 틀린 것부터 나와요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("최근 추가 단어 비율", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState())) {
                listOf(50, 70, 80, 90, 100).forEach { n ->
                    FilterChip(
                        selected = recentShare == n,
                        onClick = { recentShare = n; settings.recentShare = n; saved() },
                        label = { Text("${n}%") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Text(
                "10문제 중 새 단어 ${recentShare / 10}문제 · 기존 단어 ${10 - recentShare / 10}문제",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("며칠 안에 넣은 단어를 '최근'으로 볼까요?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(3, 7, 14).forEach { n ->
                    FilterChip(
                        selected = recentDays == n,
                        onClick = { recentDays = n; settings.recentDays = n; saved() },
                        label = { Text("${n}일") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("카드 반복")
            Text(
                "카드 몇 장을 돌려 가며 보다가, 충분히 맞힌 카드는 빠지고 다음 카드가 들어와요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("한 번에 돌리는 카드", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(5, 10, 20, 30).forEach { n ->
                    FilterChip(
                        selected = hand == n,
                        onClick = { hand = n; settings.handSize = n; saved() },
                        label = { Text("${n}장") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Text("헷갈려요 몇 번이면 빠질까요? (외웠어요는 1번)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(1, 2, 3).forEach { n ->
                    FilterChip(
                        selected = leave == n,
                        onClick = { leave = n; settings.knownToLeave = n; saved() },
                        label = { Text("${n}번") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                Modifier.fillMaxWidth().clickable { autoSpeak = !autoSpeak; settings.autoSpeak = autoSpeak; saved() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("발음 자동 재생", style = MaterialTheme.typography.bodyLarge)
                    Text("카드가 나올 때마다 발음을 들려줘요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(checked = autoSpeak, onCheckedChange = { autoSpeak = it; settings.autoSpeak = it; saved() })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("학습 시간")
            Text("하루 최소 학습 시간", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState())) {
                listOf(0 to "끔", 5 to "5분", 10 to "10분", 15 to "15분", 20 to "20분", 30 to "30분", 60 to "60분").forEach { (n, label) ->
                    FilterChip(
                        selected = goal == n,
                        onClick = { goal = n; settings.dailyGoalMinutes = n; saved() },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Text("이 시간 동안 화면을 안 건드리면 시간이 멈춰요", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(10, 20, 30, 60).forEach { n ->
                    FilterChip(
                        selected = idle == n,
                        onClick = { idle = n; settings.idleSeconds = n; saved() },
                        label = { Text("${n}초") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("보상 쿠폰 (부모님용)")
            Text(
                "아이가 정답으로 모은 별로 받을 수 있는 보상을 정해요. 쿠폰은 복습·통계 탭에서 받아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            rewards.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎟 ${r.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("⭐ ${r.cost}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        rewards = rewards.filterIndexed { j, _ -> j != i }
                        game.rewards = rewards
                        saved()
                    }) { Text("삭제") }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = rewardName,
                    onValueChange = { rewardName = it },
                    label = { Text("보상 (예: 게임 30분)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = rewardCost,
                    onValueChange = { v -> rewardCost = v.filter { it.isDigit() }.take(4) },
                    label = { Text("별") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp),
                )
            }
            TextButton(
                onClick = {
                    rewards = rewards + Reward(rewardName.trim(), rewardCost.toInt())
                    game.rewards = rewards
                    rewardName = ""
                    saved()
                },
                enabled = rewardName.isNotBlank() && (rewardCost.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("보상 추가") }
            Text(
                "정답 1개에 ⭐1, 5콤보마다 ⭐2를 더 받아요. 하루에 보통 ⭐30~60개 정도 모여요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("비밀번호")
            Text(
                if (hasPin) "설정을 열 때 비밀번호 4자리를 물어봐요" else "아이가 설정을 바꾸지 못하게 비밀번호를 걸 수 있어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { settingPin = true }) { Text(if (hasPin) "비밀번호 바꾸기" else "비밀번호 설정") }
                if (hasPin) {
                    TextButton(onClick = { settings.setPin(null); hasPin = false; saved() }) { Text("비밀번호 끄기") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("텔레그램 알림")
            Text(
                "하루 목표 시간을 채우면 오늘의 학습 결과를 텔레그램으로 보내요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; settings.telegramToken = it },
                label = { Text("봇 토큰 (BotFather가 준 값)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = chatId,
                onValueChange = { chatId = it; settings.telegramChatId = it },
                label = { Text("채팅 ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        tgStatus = "찾는 중…"
                        Telegram.findChatId(token)
                            .onSuccess { chatId = it; settings.telegramChatId = it; tgStatus = "채팅 ID를 찾았어요" }
                            .onFailure { tgStatus = it.message }
                    }
                }, enabled = token.isNotBlank()) { Text("채팅 ID 자동으로 찾기") }
                TextButton(onClick = {
                    scope.launch {
                        tgStatus = "보내는 중…"
                        val error = Telegram.send(token, chatId, "✅ 단어장 앱과 연결됐어요! 목표를 채우면 여기로 결과를 보내드릴게요.")
                        tgStatus = error ?: "테스트 메시지를 보냈어요. 텔레그램을 확인하세요"
                    }
                }, enabled = token.isNotBlank() && chatId.isNotBlank()) { Text("테스트 보내기") }
            }
            tgStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            }

            Text(
                "맞힌 단어는 처음 1일 뒤에 복습으로 나오고, 맞힐 때마다 간격이 1.4배(외웠어요는 2.2배)로 늘어요. " +
                    "틀리면 간격이 절반으로 줄고 오늘 다시 나와요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier,
    )
}

/** Shown instead of the settings while a password is set and not yet entered. */
@Composable
private fun PinLock(check: (String) -> Boolean, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔒", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text("설정은 비밀번호가 필요해요", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        PinField(
            value = pin,
            onChange = {
                pin = it
                error = false
                if (it.length == 4) {
                    if (check(it)) onUnlocked() else { error = true; pin = "" }
                }
            },
            label = "비밀번호 4자리",
            modifier = Modifier.focusRequester(focus),
        )
        if (error) {
            Text("비밀번호가 달라요", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Asks for a new 4-digit password twice. */
@Composable
private fun NewPinDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val mismatch = second.length == 4 && first != second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("비밀번호 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(first, { first = it }, "새 비밀번호 4자리")
                PinField(second, { second = it }, "한 번 더 입력")
                if (mismatch) Text("두 비밀번호가 달라요", color = MaterialTheme.colorScheme.error)
                Text(
                    "비밀번호를 잊으면 앱을 지웠다 다시 깔아야 하고, 그러면 단어도 지워져요. 꼭 기억해 두세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(first) }, enabled = first.length == 4 && first == second) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
