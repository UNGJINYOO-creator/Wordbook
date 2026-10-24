package com.wordbook.app.ui

import androidx.compose.foundation.background
import com.wordbook.app.data.Backup
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.wordbook.app.data.Reminder
import com.wordbook.app.data.Report
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
    var reminderHour by remember { mutableStateOf(settings.reminderHour) }
    var textScale by remember { mutableStateOf(settings.textScale) }
    var alertCoupon by remember { mutableStateOf(settings.alertCoupon) }
    var alertStreak by remember { mutableStateOf(settings.alertStreak) }
    var alertEvening by remember { mutableStateOf(settings.alertEvening) }
    var dailyWords by remember { mutableStateOf(settings.dailyWords.takeIf { it > 0 }?.toString() ?: "") }
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
    var soundEffects by remember { mutableStateOf(settings.soundEffects) }
    var testSize by remember { mutableStateOf(settings.testSize) }
    var comboSound by remember { mutableStateOf(settings.comboSound) }
    val sfx = remember { com.wordbook.app.data.Sfx(context) }
    androidx.compose.runtime.DisposableEffect(sfx) { onDispose { sfx.release() } }
    var telegramBackup by remember { mutableStateOf(settings.telegramBackup) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var restored by remember { mutableStateOf(false) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val error = runCatching { Backup.save(context, uri) }.exceptionOrNull()
            backupStatus = error?.let { "백업 실패: ${it.message}" } ?: "백업 파일을 저장했어요"
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val error = runCatching { Backup.restore(context, uri) }.exceptionOrNull()
            if (error == null) restored = true else backupStatus = "불러오기 실패: ${error.message}"
        }
    }
    if (restored) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("백업을 불러왔어요") },
            text = { Text("앱을 다시 시작하면 백업한 내용으로 바뀌어요.") },
            confirmButton = {
                TextButton(onClick = {
                    // Restart so every screen and setting reads the restored files.
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                }) { Text("다시 시작") }
            },
        )
    }
    val game = remember { Game(context) }
    var rewards by remember { mutableStateOf(game.rewards) }
    var rewardName by remember { mutableStateOf("") }
    var rewardCost by remember { mutableStateOf("50") }
    var settingPin by remember { mutableStateOf(false) }
    var wizard by remember { mutableStateOf(false) }
    var fishReset by remember { mutableStateOf(0) }
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

    if (wizard) {
        SetupWizard(settings, onSetPin = { settingPin = true }, onClose = {
            wizard = false
            // The wizard saved straight to settings; show its choices here too.
            modes = settings.modes; staged = settings.staged
            dailyWords = settings.dailyWords.takeIf { it > 0 }?.toString() ?: ""
            goal = settings.dailyGoalMinutes; size = settings.sessionSize; testSize = settings.testSize
            reminderHour = settings.reminderHour; textScale = settings.textScale
            autoSpeak = settings.autoSpeak; soundEffects = settings.soundEffects
            saved()
        })
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
            Column(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .clickable { wizard = true }
                    .padding(16.dp),
            ) {
                Text("🧭 설정 길잡이", style = MaterialTheme.typography.titleMedium)
                Text(
                    "중요한 설정 8가지를 팝업으로 한 장씩 차례로 정해요. ⭐ 추천 설정으로 한 번에 맞출 수도 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            GroupHeader("📚 학습 방법")
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
            Section("카드 순서")
            ToggleRow("카드 섞기", "학습할 때마다 순서를 무작위로 (끄면 알파벳 순)", shuffle) {
                shuffle = it; settings.shuffle = it; saved()
            }
            ToggleRow("뒷면 먼저", "뜻을 먼저 보고 영어 단어 떠올리기", backFirst) {
                backFirst = it; settings.backFirst = it; saved()
            }
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
            ToggleRow("효과음", "콤보·레벨 업 때 소리를 내요", soundEffects) {
                soundEffects = it; settings.soundEffects = it
            }
            if (soundEffects) {
                Text("콤보 소리 (누르면 들어 볼 수 있어요)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    com.wordbook.app.data.Sfx.names.forEachIndexed { i, name ->
                        FilterChip(
                            selected = comboSound == i,
                            onClick = { comboSound = i; settings.comboSound = i; sfx.combo(i, 3) },
                            label = { Text(name) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("카드 글자 크기")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(0.85f to "작게", 1f to "보통", 1.2f to "크게").forEach { (v, label) ->
                    FilterChip(
                        selected = textScale == v,
                        onClick = { textScale = v; settings.textScale = v; saved() },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            GroupHeader("🎯 목표와 학습량")
            Section("하루 외울 단어")
            OutlinedTextField(
                value = dailyWords,
                onValueChange = { v ->
                    dailyWords = v.filter { it.isDigit() }.take(3)
                    settings.dailyWords = dailyWords.toIntOrNull() ?: 0
                },
                label = { Text("하루 목표 (단어 수)") },
                suffix = { Text("개") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).width(200.dp),
            )
            Text(
                "객관식·빈칸·듣고 쓰기에서 맞힌 단어만 세요(뜻 카드 스와이프는 안 세요). 오늘 학습에 새 단어도 이만큼 나와요. 비우면 목표 없음.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("학습 시간")
            Text("하루 최소 학습 시간", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf(0 to "끔", 5 to "5분", 10 to "10분", 15 to "15분", 20 to "20분", 30 to "30분", 60 to "60분").forEach { (n, label) ->
                    FilterChip(
                        selected = goal == n,
                        onClick = { goal = n; settings.dailyGoalMinutes = n; saved() },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                CustomNumber(goal, listOf(0, 5, 10, 15, 20, 30, 60), "분", 180) { goal = it; settings.dailyGoalMinutes = it }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("한 번에 학습할 카드 수")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf(0 to "전부", 10 to "10", 20 to "20", 30 to "30", 50 to "50").forEach { (n, label) ->
                    FilterChip(
                        selected = size == n,
                        onClick = { size = n; settings.sessionSize = n; saved() },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                CustomNumber(size, listOf(0, 10, 20, 30, 50), "장", 999) { size = it; settings.sessionSize = it }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("시험 문제 수")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf(10, 20, 30, 50).forEach { n ->
                    FilterChip(
                        selected = testSize == n,
                        onClick = { testSize = n; settings.testSize = n; saved() },
                        label = { Text("${n}문제") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                CustomNumber(testSize, listOf(10, 20, 30, 50), "문제", 200) { testSize = it; settings.testSize = it }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("저녁 알림")
            Text(
                "이 시간까지 오늘 목표를 못 채우면 휴대폰에 알림을 띄워요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState())) {
                listOf(-1 to "끔", 18 to "오후 6시", 19 to "7시", 20 to "8시", 21 to "9시", 22 to "10시").forEach { (h, label) ->
                    FilterChip(
                        selected = reminderHour == h,
                        onClick = {
                            reminderHour = h
                            settings.reminderHour = h
                            Reminder.schedule(context)
                            saved()
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            GroupHeader("🎣 오늘의 단어 낚시 도전!")
            Text(
                "오늘 목표를 채우면 열리는 낚시 게임이에요. 맞히면 물고기를 끌어오고, 틀리거나 가만히 있으면 멀어져요. " +
                    "아이 수준에 맞게 몇 번 해 보고 조절하세요. 맞혀서 끌어오는 거리가 도망가는 거리보다 넉넉해야 낚을 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            androidx.compose.runtime.key(fishReset) {
            // Stored as the old time values: distance = fishGoal - time, so the line snaps at fishGoal metres.
            FishNumber("시작 거리", "m", settings.fishGoal - settings.fishStart, 5, 1190) {
                settings.fishStart = (settings.fishGoal - it).coerceAtLeast(1)
            }
            FishNumber("이만큼 멀어지면 줄이 끊어져요", "m", settings.fishGoal, 20, 1200) {
                val start = settings.fishGoal - settings.fishStart
                settings.fishGoal = it
                settings.fishStart = (it - start).coerceAtLeast(1)
            }
            FishNumber("맞히면 끌어오는 거리 (빈칸·듣고 쓰기는 +2m)", "m", settings.fishRight, 1, 60) { settings.fishRight = it }
            FishNumber("틀리면 멀어지는 거리", "m", settings.fishWrong, 0, 60) { settings.fishWrong = it }
            FishNumber("처음: 10초마다 도망가는 거리", "m", settings.fishDrainStart, 0, 50) { settings.fishDrainStart = it }
            FishNumber("가까울 때: 10초마다 도망가는 거리", "m", settings.fishDrainEnd, 0, 50) { settings.fishDrainEnd = it }
            FishNumber("하루 도전 횟수 (0 = 제한 없음)", "번", settings.fishTries, 0, 20) { settings.fishTries = it }
            }
            TextButton(
                onClick = {
                    settings.fishStart = 100; settings.fishGoal = 200; settings.fishRight = 5; settings.fishWrong = 3
                    settings.fishDrainStart = 5; settings.fishDrainEnd = 12; settings.fishTries = 0
                    fishReset++
                    saved()
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("기본값으로 되돌리기 (100m에서 시작, 200m면 끊어짐, 5m / 3m, 횟수 제한 없음)") }
            GroupHeader("👨‍👩‍👧 부모님")
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
            Section("보상 쿠폰")
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
            Section("텔레그램 알림")
            Text(
                "하루 최소 학습 시간을 채우면 오늘의 학습 리포트를 텔레그램으로 보내요. 엄마·아빠 모두 받으려면 각자 봇에게 /start를 보낸 뒤 '채팅 ID 자동으로 찾기'를 누르세요.",
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Row(Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        tgStatus = "찾는 중…"
                        Telegram.findChatIds(token)
                            .onSuccess { found ->
                                // Keep the ids already saved: Telegram only remembers the last day's messages.
                                val all = (Telegram.splitIds(chatId) + found).distinct().take(2)
                                chatId = all.joinToString(", ")
                                settings.telegramChatId = chatId
                                tgStatus = "${all.size}명에게 보낼게요"
                            }
                            .onFailure { tgStatus = it.message }
                    }
                }, enabled = token.isNotBlank()) { Text("채팅 ID 자동으로 찾기") }
                TextButton(onClick = {
                    scope.launch {
                        tgStatus = "보내는 중…"
                        val error = Telegram.send(token, chatId, "✅ 낚아보카와 연결됐어요! 목표를 채우면 여기로 결과를 보내드릴게요.")
                        tgStatus = error ?: "테스트 메시지를 보냈어요. 텔레그램을 확인하세요"
                    }
                }, enabled = token.isNotBlank() && chatId.isNotBlank()) { Text("테스트 보내기") }
            }
            TextButton(
                onClick = {
                    scope.launch {
                        tgStatus = "보내는 중…"
                        val report = Report.build(context)
                        val error = Telegram.send(token, chatId, report)
                        tgStatus = error ?: "오늘 학습 결과를 보냈어요"
                    }
                },
                enabled = token.isNotBlank() && chatId.isNotBlank(),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("지금 오늘 학습 결과 보내기") }
            tgStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            }
            ToggleRow("쿠폰을 받으면 알림", "아이가 별로 보상 쿠폰을 받는 순간 알려요", alertCoupon) {
                alertCoupon = it; settings.alertCoupon = it
            }
            ToggleRow("연속 학습이 끊기면 알림", "이틀 이상 이어지던 공부를 하루 쉬면 다음 날 알려요", alertStreak) {
                alertStreak = it; settings.alertStreak = it
            }
            ToggleRow("저녁에 목표를 못 채웠으면 알림", "저녁 알림 시간에 아직 남은 양을 알려요", alertEvening) {
                alertEvening = it; settings.alertEvening = it
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("백업")
            Text(
                "휴대폰을 바꿀 때를 대비해 단어장, 학습 기록, 별·레벨, 설정을 파일 하나로 저장해요. 구글 드라이브나 카톡 나에게 보내기로 보관하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("💾 백업하기", { exportBackup.launch(Backup.fileName()) }, Modifier.weight(1f))
                SecondaryButton("📂 불러오기", { importBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.weight(1f))
            }
            ToggleRow("매주 텔레그램으로 자동 백업", "일주일에 한 번 부모님 텔레그램에 백업 파일을 보내요", telegramBackup) {
                telegramBackup = it; settings.telegramBackup = it
            }
            TextButton(
                onClick = {
                    scope.launch {
                        backupStatus = "보내는 중…"
                        backupStatus = Backup.sendToTelegram(context) ?: "텔레그램으로 백업을 보냈어요"
                    }
                },
                enabled = token.isNotBlank() && chatId.isNotBlank(),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("지금 텔레그램으로 백업 보내기") }
            backupStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            }
            // Rarely changed options, folded away.
            TextButton(onClick = { advanced = !advanced }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(if (advanced) "▾ 고급 설정 접기" else "▸ 고급 설정 (카드 반복, 종합단어장, 타이머)")
            }
            if (advanced) {
            Section("카드 반복")
            Text(
                "카드 몇 장을 돌려 가며 보다가, 충분히 맞힌 카드는 빠지고 다음 카드가 들어와요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("한 번에 돌리는 카드", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf(5, 10, 20, 30).forEach { n ->
                    FilterChip(
                        selected = hand == n,
                        onClick = { hand = n; settings.handSize = n; saved() },
                        label = { Text("${n}장") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                CustomNumber(hand, listOf(5, 10, 20, 30), "장", 100) { hand = it; settings.handSize = it }
            }
            Text("헷갈려요 몇 번이면 빠질까요? (외웠어요는 1번)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf(1, 2, 3).forEach { n ->
                    FilterChip(
                        selected = leave == n,
                        onClick = { leave = n; settings.knownToLeave = n; saved() },
                        label = { Text("${n}번") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                CustomNumber(leave, listOf(1, 2, 3), "번", 9) { leave = it; settings.knownToLeave = it }
            }
            ToggleRow("틀린 카드 반복", "모르는 카드는 알 때까지 다시 나와요 (끄면 한 번씩만)", repeatMissed) {
                repeatMissed = it; settings.repeatMissed = it; saved()
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
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf(50, 70, 80, 90, 100).forEach { n ->
                    FilterChip(
                        selected = recentShare == n,
                        onClick = { recentShare = n; settings.recentShare = n; saved() },
                        label = { Text("${n}%") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                CustomNumber(recentShare, listOf(50, 70, 80, 90, 100), "%", 100) { recentShare = it; settings.recentShare = it }
            }
            Text(
                "10문제 중 새 단어 ${recentShare / 10}문제 · 기존 단어 ${10 - recentShare / 10}문제",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("며칠 안에 넣은 단어를 '최근'으로 볼까요?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf(3, 7, 14).forEach { n ->
                    FilterChip(
                        selected = recentDays == n,
                        onClick = { recentDays = n; settings.recentDays = n; saved() },
                        label = { Text("${n}일") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                CustomNumber(recentDays, listOf(3, 7, 14), "일", 60) { recentDays = it; settings.recentDays = it }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("타이머 멈춤")
            Text("이 시간 동안 화면을 안 건드리면 시간이 멈춰요", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf(10, 20, 30, 60).forEach { n ->
                    FilterChip(
                        selected = idle == n,
                        onClick = { idle = n; settings.idleSeconds = n; saved() },
                        label = { Text("${n}초") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                CustomNumber(idle, listOf(10, 20, 30, 60), "초", 600) { idle = it; settings.idleSeconds = it }
            }
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

/** A labelled number field for the fishing game; the number is saved as it is typed. */
@Composable
private fun FishNumber(label: String, suffix: String, value: Int, min: Int, max: Int, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(4)
                text.toIntOrNull()?.let { onSet(it.coerceIn(min, max)) }
            },
            suffix = { Text(suffix) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp),
        )
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
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
