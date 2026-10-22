package com.wordbook.app.ui

import androidx.compose.foundation.clickable
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
import com.wordbook.app.data.StudyMode
import com.wordbook.app.data.StudySettings
import com.wordbook.app.net.Telegram
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { StudySettings(context) }
    var mode by remember { mutableStateOf(settings.mode) }
    var blank by remember { mutableStateOf(settings.blankLevel) }
    var autoSpeak by remember { mutableStateOf(settings.autoSpeak) }
    var size by remember { mutableStateOf(settings.sessionSize) }
    var goal by remember { mutableStateOf(settings.dailyGoalMinutes) }
    var idle by remember { mutableStateOf(settings.idleSeconds) }
    var token by remember { mutableStateOf(settings.telegramToken) }
    var chatId by remember { mutableStateOf(settings.telegramChatId) }
    var tgStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppTopBar(
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("학습 설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("학습 방식")
            StudyMode.entries.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { mode = m; settings.mode = m }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == m, onClick = { mode = m; settings.mode = m })
                    Column {
                        Text(m.label, style = MaterialTheme.typography.bodyLarge)
                        Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
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
                        onClick = { blank = b; settings.blankLevel = b },
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
                        onClick = { size = n; settings.sessionSize = n },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                Modifier.fillMaxWidth().clickable { autoSpeak = !autoSpeak; settings.autoSpeak = autoSpeak }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("발음 자동 재생", style = MaterialTheme.typography.bodyLarge)
                    Text("카드가 나올 때마다 발음을 들려줘요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Switch(checked = autoSpeak, onCheckedChange = { autoSpeak = it; settings.autoSpeak = it })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Section("학습 시간")
            Text("하루 최소 학습 시간", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState())) {
                listOf(0 to "끔", 5 to "5분", 10 to "10분", 15 to "15분", 20 to "20분", 30 to "30분", 60 to "60분").forEach { (n, label) ->
                    FilterChip(
                        selected = goal == n,
                        onClick = { goal = n; settings.dailyGoalMinutes = n },
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
                        onClick = { idle = n; settings.idleSeconds = n },
                        label = { Text("${n}초") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
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
                "카드 섞기, 틀린 카드 반복, 뒷면 먼저는 단어장마다 톱니바퀴에서 정할 수 있어요.\n" +
                    "맞힌 단어는 1일, 3일, 7일, 14일, 30일 뒤에 다시 복습으로 나와요.",
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
