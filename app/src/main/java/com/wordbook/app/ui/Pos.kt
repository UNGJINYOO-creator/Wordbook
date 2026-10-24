package com.wordbook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign

val PosOptions = listOf(
    "명" to "명사", "동" to "동사", "형" to "형용사", "부" to "부사",
    "대" to "대명사", "전" to "전치사", "접" to "접속사", "조" to "조동사", "관" to "관사",
)

private fun posColor(pos: String) = when (pos) {
    "명" -> Color(0xFF1E88E5)
    "동" -> Color(0xFFE53935)
    "형" -> Color(0xFF43A047)
    "부" -> Color(0xFF8E24AA)
    "대" -> Color(0xFF00897B)
    "전" -> Color(0xFFF4511E)
    "접" -> Color(0xFF6D4C41)
    "조" -> Color(0xFFD81B60)
    "관" -> Color(0xFF546E7A)
    else -> Color(0xFF9E9E9E)
}

/** Small coloured part-of-speech label, e.g. [동사]. Shows nothing for an unknown part of speech. */
@Composable
fun PosBadge(pos: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp) {
    val label = PosOptions.firstOrNull { it.first == pos }?.second ?: return
    Text(
        label,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(posColor(pos), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Badge and meaning on one line, e.g. [명사] 군대, 육군; long meanings wrap after the badge. */
@Composable
fun PosMeaning(pos: String, meaning: String, fontSize: TextUnit, badgeSize: TextUnit, modifier: Modifier = Modifier) {
    val label = PosOptions.firstOrNull { it.first == pos }?.second
    if (label == null) {
        Text(meaning, fontSize = fontSize, textAlign = TextAlign.Center, modifier = modifier)
        return
    }
    val text = buildAnnotatedString {
        appendInlineContent("pos", label)
        append(" ")
        append(meaning)
    }
    val inline = mapOf(
        "pos" to InlineTextContent(
            Placeholder((label.length * badgeSize.value + 12).sp, (badgeSize.value * 1.6f).sp, PlaceholderVerticalAlign.TextCenter),
        ) { PosBadge(pos, fontSize = badgeSize) },
    )
    Text(text, inlineContent = inline, fontSize = fontSize, textAlign = TextAlign.Center, modifier = modifier)
}

/** Part-of-speech badge that opens a menu to correct it. */
@Composable
fun PosChips(pos: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row {
        Spacer(Modifier.width(6.dp))
        Box {
            if (pos.isEmpty()) {
                Text(
                    "품사?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable { open = true }.padding(4.dp),
                )
            } else {
                PosBadge(pos, Modifier.clickable { open = true })
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PosOptions.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onChange(code); open = false })
                }
                DropdownMenuItem(text = { Text("모름") }, onClick = { onChange(""); open = false })
            }
        }
    }
}
