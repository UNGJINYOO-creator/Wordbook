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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
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

/**
 * Guesses the part of speech of one meaning part from its Korean ending:
 * "~하다" is a verb, "~한, ~의" an adjective, "~게, ~히" an adverb, anything else a noun.
 */
fun guessPos(part: String): String {
    val t = part.replace(Regex("\\(.*?\\)"), "").trim().trimEnd('.', ',')
    val last = t.split(',').last().trim()
    return when {
        last.endsWith("다") -> "동"
        listOf("한", "운", "은", "는", "된", "의", "인", "던", "른", "픈", "쁜", "긴", "친", "난").any { last.endsWith(it) } -> "형"
        listOf("게", "히").any { last.endsWith(it) } && last.length >= 2 -> "부"
        else -> "명"
    }
}

/**
 * Meaning on one line with its part-of-speech badges, e.g. [명사] 군대, 육군 · [동사] ~처럼 들리다.
 * The first meaning is bold and larger; parts after " / " get their own badge when it differs.
 */
@Composable
fun PosMeaning(pos: String, meaning: String, fontSize: TextUnit, badgeSize: TextUnit, modifier: Modifier = Modifier) {
    val parts = meaning.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(meaning) }
    // Only guess for the four common parts of speech; 대명사, 조동사 etc. keep the one label.
    val guessing = pos in listOf("명", "동", "형", "부")
    val badges = mutableMapOf<String, String>()
    val text = buildAnnotatedString {
        parts.forEachIndexed { i, part ->
            val p = if (i == 0) pos else if (guessing) guessPos(part) else ""
            val prev = if (i == 0) "" else if (i == 1) pos else if (guessing) guessPos(parts[i - 1]) else ""
            if (i > 0) append("  ")
            if (PosOptions.any { it.first == p } && (i == 0 || p != prev)) {
                val id = "pos$i"
                badges[id] = p
                appendInlineContent(id, p)
                append(" ")
            } else if (i > 0) {
                append("· ")
            }
            if (i == 0) {
                // 대표 뜻: the first meaning stands out, the rest is smaller.
                val head = part.substringBefore(",")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(head) }
                val rest = part.substring(head.length)
                if (rest.isNotEmpty()) withStyle(SpanStyle(fontSize = fontSize * 0.75f)) { append(rest) }
            } else {
                withStyle(SpanStyle(fontSize = fontSize * 0.75f)) { append(part) }
            }
        }
    }
    val inline = badges.mapValues { (_, p) ->
        val label = PosOptions.first { it.first == p }.second
        InlineTextContent(
            Placeholder((label.length * badgeSize.value + 12).sp, (badgeSize.value * 1.6f).sp, PlaceholderVerticalAlign.TextCenter),
        ) { PosBadge(p, fontSize = badgeSize) }
    }
    Text(text, inlineContent = inline, fontSize = fontSize, lineHeight = 1.45.em, textAlign = TextAlign.Center, modifier = modifier)
}

/** "English sentence (한글 해석)": the English in italics, the translation smaller below. */
@Composable
fun ExampleText(example: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val en = example.substringBefore(" (").trim()
    val ko = example.substringAfter(" (", "").removeSuffix(")").trim()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(en, fontSize = fontSize, lineHeight = 1.45.em, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ko.isNotEmpty()) {
            Text(ko, fontSize = fontSize * 0.85f, lineHeight = 1.45.em, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
        }
    }
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
