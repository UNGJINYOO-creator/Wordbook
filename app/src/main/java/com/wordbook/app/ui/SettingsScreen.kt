package com.wordbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.BlankLevel
import com.wordbook.app.data.StudyMode
import com.wordbook.app.data.StudySettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { StudySettings(context) }
    var mode by remember { mutableStateOf(settings.mode) }
    var blank by remember { mutableStateOf(settings.blankLevel) }
    var autoSpeak by remember { mutableStateOf(settings.autoSpeak) }
    var size by remember { mutableStateOf(settings.sessionSize) }

    Scaffold(
        topBar = {
            TopAppBar(
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
