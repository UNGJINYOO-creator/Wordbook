package com.wordbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordbook.app.data.Word
import kotlinx.coroutines.delay

private val RightGreen = Color(0xFF2E7D32)
private val WrongRed = Color(0xFFC62828)

/** Meaning with its part of speech, used as the question for choice/spelling quizzes. */
@Composable
private fun MeaningPrompt(word: Word) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (word.pos.isNotEmpty()) {
            PosBadge(word.pos, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
        }
        Text(word.meaning.ifBlank { "(뜻 없음)" }, fontSize = 26.sp, textAlign = TextAlign.Center)
    }
}

/** Correct answers move on by themselves; wrong ones wait so the right answer can be read. */
@Composable
private fun AutoAdvance(answered: Boolean?, onNext: () -> Unit) {
    LaunchedEffect(answered) {
        if (answered == true) {
            delay(700)
            onNext()
        }
    }
    if (answered == false) {
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("다음") }
    }
}

@Composable
fun ChoiceQuiz(
    word: Word,
    choices: List<Word>,
    reverse: Boolean,
    answered: Boolean?,
    pickedId: Long?,
    onPick: (Word) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (reverse) "알맞은 뜻을 고르세요" else "알맞은 단어를 고르세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        if (reverse) Text(word.text, fontSize = 36.sp, fontWeight = FontWeight.Bold) else MeaningPrompt(word)
        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.forEach { c ->
                val label = if (reverse) c.meaning else c.text
                val colors = when {
                    answered != null && c.id == word.id ->
                        ButtonDefaults.outlinedButtonColors(containerColor = RightGreen, contentColor = Color.White)
                    answered == false && c.id == pickedId ->
                        ButtonDefaults.outlinedButtonColors(containerColor = WrongRed, contentColor = Color.White)
                    else -> ButtonDefaults.outlinedButtonColors()
                }
                OutlinedButton(
                    onClick = { if (answered == null) onPick(c) },
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(label, fontSize = 18.sp, maxLines = 2, textAlign = TextAlign.Center) }
            }
        }
        AutoAdvance(answered, onNext)
    }
}

/**
 * Spelling (fill the blanks in [pattern]) or dictation (no pattern, listen and type).
 * The whole word is typed either way, which is simpler on a phone keyboard than single letters.
 */
@Composable
fun TypingQuiz(
    word: Word,
    pattern: String?,
    answered: Boolean?,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember(word.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(word.id) { runCatching { focus.requestFocus() } }
    val dictation = pattern == null

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (dictation) "발음을 듣고 단어를 쓰세요" else "빈칸을 채워 단어를 쓰세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        if (dictation) {
            FilledIconButton(onClick = onSpeak, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, "다시 듣기", modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(12.dp))
            if (word.pos.isNotEmpty()) PosBadge(word.pos, fontSize = 16.sp)
            // The meaning would give the answer away before the word is heard; show it afterwards.
            if (answered != null) {
                Spacer(Modifier.height(8.dp))
                Text(word.meaning, fontSize = 20.sp, textAlign = TextAlign.Center)
            }
        } else {
            MeaningPrompt(word)
            Spacer(Modifier.height(24.dp))
            Text(
                pattern!!.map { if (it == '_') '_' else it }.joinToString(" "),
                fontSize = 32.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { if (answered == null) input = it },
            singleLine = true,
            label = { Text("영어 단어") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (answered == null && input.isNotBlank()) onSubmit(input) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        when (answered) {
            null -> Button(
                onClick = { onSubmit(input) },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("확인") }
            true -> Text("정답!", color = RightGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            false -> Text("정답: ${word.text}", color = WrongRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        AutoAdvance(answered, onNext)
    }
}
