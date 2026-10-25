package com.wordbook.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.Backup
import com.wordbook.app.data.BlankLevel
import com.wordbook.app.data.DictationPass
import com.wordbook.app.data.Game
import com.wordbook.app.data.Reminder
import com.wordbook.app.data.Sfx
import com.wordbook.app.data.StudyMode
import com.wordbook.app.data.StudySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onAdd: (AddKind) -> Unit = {}) {
    val context = LocalContext.current
    val settings = remember { StudySettings(context) }
    val game = remember { Game(context) }
    val scope = rememberCoroutineScope()
    // Settings apply immediately; a short toast confirms that the change was saved.
    val saved = { Toast.makeText(context, "저장됐어요", Toast.LENGTH_SHORT).show() }
    val sfx = remember { Sfx(context) }
    DisposableEffect(sfx) { onDispose { sfx.release() } }
    var hasPin by remember { mutableStateOf(settings.hasPin) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    // Only one group is open at a time, so the screen stays short.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    var settingPin by remember { mutableStateOf(false) }
    var wizard by remember { mutableStateOf(false) }
    // Bumped when the wizard closes, so every row reads the settings again.
    var refresh by remember { mutableIntStateOf(0) }
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
        SetupWizard(settings, hasPin, onSetPin = { settingPin = true }, onClose = {
            wizard = false
            refresh++
            saved()
        })
    }
    val toggle = { name: String -> open = if (open == name) null else name }

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
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .clickable { wizard = true }
                    .padding(16.dp),
            ) {
                Text("🧭 설정 길잡이로 한 번에 정하기", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                Text(
                    "모든 설정을 팝업으로 한 장씩 차례로 정해요. ⭐ 추천 설정을 한 번에 맞출 수도 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text("➕ 단어 추가", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("사진·스크린샷으로 단어를 넣거나 단어장을 만들어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("📷 사진 찍기", { onAdd(AddKind.CAMERA) }, Modifier.weight(1f))
                    SecondaryButton("🖼 앨범에서", { onAdd(AddKind.ALBUM) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("📒 빈 단어장", { onAdd(AddKind.DECK) }, Modifier.weight(1f))
                    SecondaryButton("📚 종합단어장", { onAdd(AddKind.COLLECTION) }, Modifier.weight(1f))
                }
            }
            Text(
                "아래 항목을 누르면 펼쳐져요. 바꾸면 바로 저장돼요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            key(refresh) {
                var dailyWords by remember { mutableIntStateOf(settings.dailyWords) }
                var goal by remember { mutableIntStateOf(settings.dailyGoalMinutes) }
                var reminderHour by remember { mutableIntStateOf(settings.reminderHour) }
                var modes by remember { mutableStateOf(settings.modes) }
                var staged by remember { mutableStateOf(settings.staged) }
                var pass by remember { mutableStateOf(settings.dictationPass) }
                var blank by remember { mutableStateOf(settings.blankLevel) }
                var size by remember { mutableIntStateOf(settings.sessionSize) }
                var testSize by remember { mutableIntStateOf(settings.testSize) }
                var shuffle by remember { mutableStateOf(settings.shuffle) }
                var backFirst by remember { mutableStateOf(settings.backFirst) }
                var textScale by remember { mutableStateOf(settings.textScale) }
                var autoSpeak by remember { mutableStateOf(settings.autoSpeak) }
                var soundEffects by remember { mutableStateOf(settings.soundEffects) }
                var comboSound by remember { mutableIntStateOf(settings.comboSound) }
                var fishLevel by remember { mutableStateOf(settings.fishLevel()) }
                var fishTries by remember { mutableIntStateOf(settings.fishTries) }
                var fishDetail by remember { mutableStateOf(false) }
                var fishReset by remember { mutableIntStateOf(0) }
                var rewardCount by remember { mutableIntStateOf(game.rewards.size) }
                var hand by remember { mutableIntStateOf(settings.handSize) }
                var leave by remember { mutableIntStateOf(settings.knownToLeave) }
                var repeatMissed by remember { mutableStateOf(settings.repeatMissed) }
                var recentShare by remember { mutableIntStateOf(settings.recentShare) }
                var recentDays by remember { mutableIntStateOf(settings.recentDays) }
                var idle by remember { mutableIntStateOf(settings.idleSeconds) }

                Group(
                    "🎯 하루 목표",
                    listOf(
                        if (dailyWords > 0) "단어 ${dailyWords}개" else "단어 목표 없음",
                        if (goal > 0) "${goal}분" else "시간 목표 없음",
                        "알림 ${Opt.reminderLabel(reminderHour)}",
                    ).joinToString(" · "),
                    open == "goal", { toggle("goal") },
                ) {
                    Body {
                        Labeled("하루 외울 단어", "객관식·빈칸·듣고 쓰기에서 맞힌 단어만 세요. 오늘 학습에 새 단어도 이만큼 나와요.") {
                            NumberChoice(dailyWords, Opt.dailyWords, "개", 300, min = 0) { dailyWords = it; settings.dailyWords = it }
                        }
                        Labeled("하루 최소 학습 시간", "채우면 오늘 목표 달성, 텔레그램으로 결과가 가요") {
                            NumberChoice(goal, Opt.minutes, "분", 180, min = 0) { goal = it; settings.dailyGoalMinutes = it }
                        }
                        Labeled("저녁 알림", "이 시간까지 목표를 못 채우면 알림을 띄워요. 직접 칸은 24시간제 (예: 18)") {
                            NumberChoice(reminderHour, Opt.reminder, "시", 23, min = 0) {
                                reminderHour = it; settings.reminderHour = it; Reminder.schedule(context)
                            }
                        }
                    }
                }
                Group(
                    "📚 학습 방법",
                    modes.joinToString(" → ") { it.label } + " · " + (if (size > 0) "${size}장씩" else "전부"),
                    open == "study", { toggle("study") },
                ) {
                    Body {
                        Text("학습 순서", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "켜 둔 단계가 위에서부터 순서대로 진행돼요. ▲▼로 순서를 바꿀 수 있어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    ModePicker(modes, staged) { m, st ->
                        modes = m; staged = st; settings.modes = m; settings.staged = st
                    }
                    Body {
                        Labeled("빈칸 난이도", "자동은 잘 외운 단어일수록 빈칸이 늘어나요") {
                            Choice(blank, BlankLevel.entries.map { it to it.label }) { blank = it; settings.blankLevel = it }
                        }
                        if (StudyMode.DICTATION in modes) {
                            Labeled("듣고 쓰기에서 '패스'를 누르면 빈칸으로", "소리가 꺼져 있으면 듣고 쓰기는 자동으로 빈칸 문제로 바뀌어요") {
                                Choice(pass, DictationPass.entries.map { it to it.label }) { pass = it; settings.dictationPass = it }
                            }
                        }
                        Labeled("한 번에 학습할 카드") {
                            NumberChoice(size, Opt.session, "장", 999, min = 0) { size = it; settings.sessionSize = it }
                        }
                        Labeled("시험 문제 수") {
                            NumberChoice(testSize, Opt.test, "문제", 200) { testSize = it; settings.testSize = it }
                        }
                    }
                }
                Group(
                    "🃏 카드와 소리",
                    listOf(
                        if (shuffle) "섞기" else "알파벳 순",
                        "글자 " + (Opt.textScale.firstOrNull { it.first == textScale }?.second ?: "보통"),
                        if (autoSpeak) "발음 켬" else "발음 끔",
                    ).joinToString(" · "),
                    open == "card", { toggle("card") },
                ) {
                    Body {
                        SwitchRow("카드 섞기", "학습할 때마다 순서를 무작위로 (끄면 알파벳 순)", shuffle) { shuffle = it; settings.shuffle = it }
                        SwitchRow("뒷면 먼저", "뜻을 먼저 보고 영어 단어 떠올리기", backFirst) { backFirst = it; settings.backFirst = it }
                        Labeled("글자 크기") {
                            Choice(textScale, Opt.textScale) { textScale = it; settings.textScale = it }
                        }
                        SwitchRow("발음 자동 재생", "카드가 나올 때마다 발음을 들려줘요", autoSpeak) { autoSpeak = it; settings.autoSpeak = it }
                        SwitchRow("효과음", "콤보·레벨 업 때 소리를 내요", soundEffects) { soundEffects = it; settings.soundEffects = it }
                        if (soundEffects) {
                            Labeled("콤보 소리 (누르면 들어 봐요)") {
                                Choice(comboSound, Sfx.names.mapIndexed { i, n -> i to n }) {
                                    comboSound = it; settings.comboSound = it; sfx.combo(it, 3)
                                }
                            }
                        }
                    }
                }
                Group(
                    "🎣 단어 낚시 게임",
                    "난이도 ${fishLevel?.name ?: "직접 조정"} · " + (if (fishTries > 0) "하루 ${fishTries}번" else "횟수 무제한"),
                    open == "fish", { toggle("fish") },
                ) {
                    Body {
                        Text(
                            "오늘 목표를 채우면 열리는 낚시 게임이에요. 맞히면 물고기를 끌어오고, 틀리거나 가만히 있으면 멀어져요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Labeled("난이도") {
                            Choice(fishLevel, FishLevels.map { it to it.name }) { level ->
                                fishLevel = level; level?.let(settings::applyFish); fishReset++
                            }
                        }
                        Labeled("하루 도전 횟수") {
                            NumberChoice(fishTries, Opt.tries, "번", 20, min = 0) { fishTries = it; settings.fishTries = it }
                        }
                        TextButton(onClick = { fishDetail = !fishDetail }) {
                            Text(if (fishDetail) "▾ 세부 거리 접기" else "▸ 세부 거리 직접 조정")
                        }
                        if (fishDetail) key(fishReset) {
                            Text(
                                "맞혀서 끌어오는 거리가 도망가는 거리보다 넉넉해야 낚을 수 있어요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            val changed = { fishLevel = settings.fishLevel() }
                            // Stored as the old time values: distance = fishGoal - time, so the line snaps at fishGoal metres.
                            FishNumber("시작 거리", "m", settings.fishGoal - settings.fishStart, 5, 1190) {
                                settings.fishStart = (settings.fishGoal - it).coerceAtLeast(1); changed()
                            }
                            FishNumber("이만큼 멀어지면 줄이 끊어져요", "m", settings.fishGoal, 20, 1200) {
                                val start = settings.fishGoal - settings.fishStart
                                settings.fishGoal = it
                                settings.fishStart = (it - start).coerceAtLeast(1); changed()
                            }
                            FishNumber("맞히면 끌어오는 거리 (빈칸·듣고 쓰기 +2m)", "m", settings.fishRight, 1, 60) { settings.fishRight = it; changed() }
                            FishNumber("틀리면 멀어지는 거리", "m", settings.fishWrong, 0, 60) { settings.fishWrong = it; changed() }
                            FishNumber("처음: 10초마다 도망가는 거리", "m", settings.fishDrainStart, 0, 50) { settings.fishDrainStart = it; changed() }
                            FishNumber("가까울 때: 10초마다 도망가는 거리", "m", settings.fishDrainEnd, 0, 50) { settings.fishDrainEnd = it; changed() }
                        }
                    }
                }
                Group(
                    "👨‍👩‍👧 비밀번호와 보상",
                    (if (hasPin) "비밀번호 켬" else "비밀번호 없음") + " · 보상 ${rewardCount}개",
                    open == "parent", { toggle("parent") },
                ) {
                    Body {
                        Text("비밀번호", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (hasPin) "설정을 열 때 비밀번호 4자리를 물어봐요" else "아이가 설정을 바꾸지 못하게 비밀번호를 걸 수 있어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Row {
                            TextButton(onClick = { settingPin = true }) { Text(if (hasPin) "비밀번호 바꾸기" else "비밀번호 설정") }
                            if (hasPin) {
                                TextButton(onClick = { settings.setPin(null); hasPin = false; saved() }) { Text("비밀번호 끄기") }
                            }
                        }
                        Labeled("보상 쿠폰", "아이가 단어를 외워 낚은 물고기로 받을 수 있는 보상이에요. 쿠폰은 복습·통계 탭에서 받아요.") {
                            RewardEditor(game) { rewardCount = game.rewards.size; saved() }
                        }
                    }
                }
                Group(
                    "📨 텔레그램 알림",
                    if (settings.telegramToken.isNotBlank() && settings.telegramChatId.isNotBlank()) "연결됨" else "연결 안 됨",
                    open == "telegram", { toggle("telegram") },
                ) {
                    Body {
                        TelegramEditor(settings)
                        ParentAlerts(settings)
                    }
                }
                if (com.wordbook.app.sync.FirebaseConfig.ready) {
                    val familyCode = com.wordbook.app.sync.FamilySync.code(context)
                    Group(
                        "🔄 기기 동기화",
                        if (familyCode != null) "연결됨 · 가족 코드 ${com.wordbook.app.sync.FamilySync.shown(familyCode)}" else "태블릿·폰 진도 맞추기",
                        open == "sync", { toggle("sync") },
                    ) {
                        Body { SyncEditor() }
                    }
                }
                Group("💾 백업", "휴대폰을 바꿀 때를 대비해 저장해 둬요", open == "backup", { toggle("backup") }) {
                    Body {
                        Text(
                            "단어장, 학습 기록, 물고기·레벨, 설정을 파일 하나로 저장해요. 구글 드라이브나 카톡 나에게 보내기로 보관하세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("💾 백업하기", { exportBackup.launch(Backup.fileName()) }, Modifier.weight(1f))
                            SecondaryButton("📂 불러오기", { importBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    backupStatus = "보내는 중…"
                                    backupStatus = Backup.sendToTelegram(context) ?: "텔레그램으로 백업을 보냈어요"
                                }
                            },
                            enabled = settings.telegramToken.isNotBlank() && settings.telegramChatId.isNotBlank(),
                        ) { Text("지금 텔레그램으로 백업 보내기") }
                        Text(
                            "매주 자동 백업은 📨 텔레그램 알림에서 켜고 끌 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        backupStatus?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Group(
                    "⚙️ 고급",
                    "카드 ${hand}장씩 돌리기 · 최근 단어 ${recentShare}% · ${idle}초 멈춤",
                    open == "advanced", { toggle("advanced") },
                ) {
                    Body {
                        Text("카드 반복", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "카드 몇 장을 돌려 가며 보다가, 충분히 맞힌 카드는 빠지고 다음 카드가 들어와요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Labeled("한 번에 돌리는 카드") {
                            NumberChoice(hand, Opt.hand, "장", 100) { hand = it; settings.handSize = it }
                        }
                        Labeled("헷갈려요 몇 번이면 빠질까요?", "외웠어요는 1번이면 빠져요") {
                            NumberChoice(leave, Opt.leave, "번", 9) { leave = it; settings.knownToLeave = it }
                        }
                        SwitchRow("틀린 카드 반복", "모르는 카드는 알 때까지 다시 나와요 (끄면 한 번씩만)", repeatMissed) {
                            repeatMissed = it; settings.repeatMissed = it
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("종합단어장", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "최근 넣은 단어를 얼마나 내고, 나머지는 복습할 때가 된 것과 자주 틀린 단어로 채워요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Labeled("최근 추가 단어 비율", "10문제 중 새 단어 ${recentShare / 10}문제 · 기존 단어 ${10 - recentShare / 10}문제") {
                            NumberChoice(recentShare, Opt.recentShare, "%", 100) { recentShare = it; settings.recentShare = it }
                        }
                        Labeled("며칠 안에 넣은 단어를 '최근'으로 볼까요?") {
                            NumberChoice(recentDays, Opt.recentDays, "일", 60) { recentDays = it; settings.recentDays = it }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("타이머 멈춤", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Labeled("이 시간 동안 화면을 안 건드리면 학습 시간이 멈춰요") {
                            NumberChoice(idle, Opt.idle, "초", 600, min = 5) { idle = it; settings.idleSeconds = it }
                        }
                        Text(
                            "맞힌 단어는 처음 1일 뒤에 복습으로 나오고, 맞힐 때마다 간격이 1.4배(외웠어요는 2.2배)로 늘어요. " +
                                "틀리면 간격이 절반으로 줄고 오늘 다시 나와요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A setting category: a header with the current values, and its rows when open. */
@Composable
private fun Group(title: String, summary: String, open: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Column(Modifier.padding(bottom = 12.dp), content = content)
        }
    }
}

/** Group rows with the usual side margin. */
@Composable
private fun Body(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
}

/** A labelled number field for the fishing game; the number is saved as it is typed. */
@Composable
private fun FishNumber(label: String, suffix: String, value: Int, min: Int, max: Int, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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

/** 가족 코드: start a family on the first device, type the code on the others; then they sync by themselves. */
@Composable
private fun SyncEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(com.wordbook.app.sync.FamilySync.code(context)) }
    var typed by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val sync = {
        busy = true
        status = "맞추는 중…"
        scope.launch(Dispatchers.IO) {
            val error = com.wordbook.app.sync.FamilySync.run(context)
            status = error?.let { "동기화하지 못했어요: $it" } ?: "다 맞췄어요 ✅"
            busy = false
        }
    }
    Text(
        "태블릿과 폰에서 같은 가족 코드를 쓰면 단어 진도, 물고기, 거대 물고기, 설정이 저절로 맞춰져요. 앱을 닫을 때와 열 때마다 맞춰요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    val c = code
    if (c == null) {
        TextButton(onClick = {
            val new = com.wordbook.app.sync.FamilySync.newCode()
            com.wordbook.app.sync.FamilySync.connect(context, new)
            code = new
            sync()
        }) { Text("➕ 새 가족 코드 만들기 (이 기기가 처음)") }
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(10) },
            label = { Text("다른 기기에 나온 가족 코드 10자리") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(enabled = typed.length == 10, onClick = {
            com.wordbook.app.sync.FamilySync.connect(context, typed)
            code = typed
            sync()
        }) { Text("🔗 이 코드로 연결") }
    } else {
        Text("가족 코드", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        Text(com.wordbook.app.sync.FamilySync.shown(c), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("다른 기기의 설정 > 🔄 기기 동기화에 이 코드를 넣으세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        val last = com.wordbook.app.sync.FamilySync.lastSync(context)
        if (last > 0) {
            Text(
                "마지막으로 맞춘 때: " + java.text.SimpleDateFormat("M월 d일 a h:mm", java.util.Locale.KOREA).format(java.util.Date(last)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row {
            TextButton(enabled = !busy, onClick = { sync() }) { Text("🔄 지금 맞추기") }
            TextButton(onClick = { com.wordbook.app.sync.FamilySync.disconnect(context); code = null; status = null }) { Text("연결 끊기") }
        }
    }
    (status ?: com.wordbook.app.sync.FamilySync.lastError(context)?.let { "지난번 동기화 오류: $it" })?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}
