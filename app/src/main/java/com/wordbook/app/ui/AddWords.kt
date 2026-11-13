package com.wordbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.DeckSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where new words go: an existing deck ([deckId] ≥ 0) or a new deck called [newName]. */
data class AddTarget(val deckId: Long, val newName: String, val label: String) {
    val isNew: Boolean get() = deckId < 0
}

fun defaultDeckName(): String = SimpleDateFormat("M월 d일 단어장", Locale.KOREA).format(Date())

private const val NEW = -1L

/**
 * 단어 추가, step 1: "어디에 넣을까요?" An existing deck gets the words and no new deck is made;
 * 새 단어장 makes one with the typed name.
 */
@Composable
fun DeckPickDialog(decks: List<DeckSummary>, onPick: (AddTarget) -> Unit, onDismiss: () -> Unit) {
    val own = decks.filter { !it.isCollection }
    var chosen by remember { mutableLongStateOf(NEW) }
    var name by remember { mutableStateOf(defaultDeckName()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어디에 넣을까요?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                PickRow("➕ 새 단어장 만들기", chosen == NEW) { chosen = NEW }
                if (chosen == NEW) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("새 단어장 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp),
                    )
                }
                if (own.isNotEmpty()) {
                    Text(
                        "기존 단어장에 넣기",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    own.forEach { d -> PickRow("📒 ${d.name} (${d.wordCount})", chosen == d.id) { chosen = d.id } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = own.firstOrNull { it.id == chosen }
                if (d != null) onPick(AddTarget(d.id, "", d.name))
                else name.trim().ifEmpty { defaultDeckName() }.let { onPick(AddTarget(NEW, it, it)) }
            }) { Text("다음") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 단어 추가, step 2: the same three ways everywhere (사진, 붙여넣기, 직접 입력). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordsSheet(
    target: String,
    isNew: Boolean,
    onCamera: () -> Unit,
    onAlbum: () -> Unit,
    onPaste: () -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("어떻게 넣을까요?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                if (isNew) "새 단어장 '$target'을 만들어서 넣어요" else "'$target' 단어장에 넣어요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            ListItem(
                headlineContent = { Text("사진·스크린샷", style = MaterialTheme.typography.titleMedium) },
                supportingContent = {
                    Column {
                        Text("책이나 화면의 큰 단어와 뜻을 찾아요", color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("📷 찍기", onCamera, Modifier.weight(1f))
                            SecondaryButton("🖼 앨범 (여러 장)", onAlbum, Modifier.weight(1f))
                        }
                    }
                },
                leadingContent = { Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SheetItem(Icons.Default.ContentPaste, "붙여넣기", "복사한 단어 목록을 붙여넣어 한 번에 넣어요", onPaste)
            SheetItem(Icons.Default.Keyboard, "직접 입력", "단어와 뜻을 손으로 적어요 (뜻을 비우면 자동 번역)", onManual)
        }
    }
}
