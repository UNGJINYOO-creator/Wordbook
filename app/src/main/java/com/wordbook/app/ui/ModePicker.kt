package com.wordbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.QuizModes
import com.wordbook.app.data.StudyMode

/**
 * Checkboxes for the ways to study (at least one stays checked), and with two or more,
 * whether they come in order of difficulty or mixed.
 */
@Composable
fun ModePicker(modes: List<StudyMode>, staged: Boolean, onChange: (modes: List<StudyMode>, staged: Boolean) -> Unit) {
    Column {
        QuizModes.forEach { m ->
            val checked = m in modes
            val toggle = {
                val next = if (checked) modes - m else modes + m
                if (next.isNotEmpty()) onChange(QuizModes.filter { it in next }, staged)
            }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = toggle).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { toggle() })
                Column {
                    Text(m.label, style = MaterialTheme.typography.bodyLarge)
                    Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (modes.size > 1) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(
                    selected = staged,
                    onClick = { onChange(modes, true) },
                    label = { Text("단계별 (외울수록 어려워져요)") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(selected = !staged, onClick = { onChange(modes, false) }, label = { Text("골고루 섞기") })
            }
        }
    }
}

/** "CARD,CHOICE" style text for passing a choice of modes between screens. */
fun encodeModes(modes: List<StudyMode>) = modes.joinToString(",") { it.name }

fun decodeModes(text: String?): List<StudyMode>? =
    text?.split(',')?.mapNotNull { n -> QuizModes.firstOrNull { it.name == n } }?.takeIf { it.isNotEmpty() }
