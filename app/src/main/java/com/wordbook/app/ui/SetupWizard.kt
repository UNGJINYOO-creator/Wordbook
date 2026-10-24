package com.wordbook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.ModePresets
import com.wordbook.app.data.Reminder
import com.wordbook.app.data.StudySettings

/**
 * A small number field that sits after a row of preset chips. It stays empty while a preset is chosen;
 * typing a number sets the value directly (clamped to [min]..[max]).
 */
@Composable
fun CustomNumber(current: Int, presets: List<Int>, suffix: String, max: Int, min: Int = 1, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current in presets) "" else current.toString()) }
    // A chip was tapped: clear the field unless it already shows that number.
    LaunchedEffect(current) { if (current in presets && text.toIntOrNull() != current) text = "" }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() }.take(max.toString().length)
            text.toIntOrNull()?.let { onSet(it.coerceIn(min, max)) }
        },
        placeholder = { Text("직접") },
        suffix = { Text(suffix) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(110.dp),
    )
}

/** Preset chips plus a 직접 입력 field, wrapping onto more lines when narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberChoice(
    current: Int,
    options: List<Pair<Int, String>>,
    suffix: String,
    max: Int,
    min: Int = 1,
    recommended: Int? = null,
    onSet: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (n, label) ->
            FilterChip(selected = current == n, onClick = { onSet(n) }, label = { Text(if (n == recommended) "⭐ $label" else label) })
        }
        CustomNumber(current, options.map { it.first }, suffix, max, min, onSet)
    }
}

private const val PIN_PAGE = 8

private class WizardStep(
    val title: String,
    val what: String,
    val how: String,
    val recommend: String? = null,
    val content: @Composable () -> Unit,
)

/**
 * 설정 길잡이: walks through the main settings one popup page at a time. Each page says what it sets and how;
 * a choice is saved as soon as it is made, so closing early keeps what was chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupWizard(settings: StudySettings, onSetPin: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var modes by remember { mutableStateOf(settings.modes) }
    var dailyWords by remember { mutableIntStateOf(settings.dailyWords) }
    var goal by remember { mutableIntStateOf(settings.dailyGoalMinutes) }
    var size by remember { mutableIntStateOf(settings.sessionSize) }
    var testSize by remember { mutableIntStateOf(settings.testSize) }
    var reminderHour by remember { mutableIntStateOf(settings.reminderHour) }
    var textScale by remember { mutableStateOf(settings.textScale) }
    var autoSpeak by remember { mutableStateOf(settings.autoSpeak) }
    var soundEffects by remember { mutableStateOf(settings.soundEffects) }

    val steps = listOf(
        WizardStep(
            "설정 길잡이",
            "중요한 설정 8가지를 한 장씩 차례로 정하는 화면이에요.",
            "각 장에서 하나를 고르고 '다음'을 누르세요. 고르는 즉시 저장되고, 나중에 설정 화면에서 언제든 바꿀 수 있어요. 숫자는 오른쪽 '직접' 칸에 원하는 만큼 써도 돼요.",
            "중학생에게 맞는 설정에 ⭐ 표시를 해 뒀어요. 잘 모르겠으면 ⭐만 골라도 돼요.",
        ) {
            FilledTonalButton(
                onClick = {
                    // Apply every ⭐ choice, then go to the PIN page, which needs the parent.
                    modes = ModePresets.first().second; settings.modes = modes; settings.staged = true
                    dailyWords = 20; settings.dailyWords = 20
                    goal = 20; settings.dailyGoalMinutes = 20
                    size = 20; settings.sessionSize = 20
                    testSize = 20; settings.testSize = 20
                    reminderHour = 20; settings.reminderHour = 20; Reminder.schedule(context)
                    textScale = 1f; settings.textScale = 1f
                    autoSpeak = true; settings.autoSpeak = true
                    soundEffects = true; settings.soundEffects = true
                    page = PIN_PAGE
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("⭐ 추천 설정으로 한 번에 맞추기") }
        },
        WizardStep(
            "① 학습 순서",
            "단어 하나를 어떤 방법으로, 어떤 순서로 공부할지 정하는 화면이에요.",
            "아래 묶음 중 하나를 누르세요. 세부 순서는 설정 > 학습 순서에서 ▲▼로 바꿀 수 있어요.",
            "처음 외울 때: 훑어보고 → 카드 → 객관식 → 빈칸 → 듣고 쓰기로 점점 어려워져서 오래 기억에 남아요.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ModePresets.forEach { (name, preset) ->
                    FilterChip(
                        selected = modes == preset,
                        onClick = { modes = preset; settings.modes = preset; settings.staged = true },
                        label = {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(if (name == ModePresets.first().first) "⭐ $name" else name, fontWeight = FontWeight.Bold)
                                Text(preset.joinToString(" → ") { it.label }, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        WizardStep(
            "② 하루 외울 단어",
            "하루에 몇 단어를 외우는 게 목표인지 정하는 화면이에요.",
            "객관식·빈칸·듣고 쓰기에서 맞힌 단어만 세요. '오늘 학습'에 새 단어도 이만큼 나와요.",
            "20개: 매일 꾸준히 하기 좋은 양이에요. 익숙해지면 30개로 늘리세요.",
        ) {
            NumberChoice(dailyWords, listOf(10 to "10개", 20 to "20개", 30 to "30개", 50 to "50개"), "개", 300, recommended = 20) {
                dailyWords = it; settings.dailyWords = it
            }
        },
        WizardStep(
            "③ 하루 학습 시간",
            "하루에 최소 몇 분 공부할지 정하는 화면이에요.",
            "이 시간을 채우면 오늘 목표 달성이 되고, 텔레그램을 연결했다면 학습 결과가 부모님께 가요.",
            "20분: 집중이 흐트러지지 않는 길이예요.",
        ) {
            NumberChoice(goal, listOf(0 to "끔", 10 to "10분", 15 to "15분", 20 to "20분", 30 to "30분", 60 to "60분"), "분", 180, recommended = 20) {
                goal = it; settings.dailyGoalMinutes = it
            }
        },
        WizardStep(
            "④ 한 번에 학습할 카드 수",
            "학습을 한 번 시작하면 카드 몇 장을 공부할지 정하는 화면이에요.",
            "'전부'는 단어장의 단어를 모두 공부해요. 처음엔 20장 정도를 추천해요.",
            "20장: 한 번에 끝내기 부담 없는 양이에요.",
        ) {
            NumberChoice(size, listOf(0 to "전부", 10 to "10", 20 to "20", 30 to "30", 50 to "50"), "장", 999, recommended = 20) {
                size = it; settings.sessionSize = it
            }
        },
        WizardStep(
            "⑤ 시험 문제 수",
            "단어장의 '📝 시험'을 누를 때 몇 문제를 낼지 정하는 화면이에요.",
            "시험 점수는 텔레그램으로 부모님께도 가요.",
            "20문제: 학교 단어 시험과 비슷한 양이에요.",
        ) {
            NumberChoice(testSize, listOf(10 to "10문제", 20 to "20문제", 30 to "30문제", 50 to "50문제"), "문제", 200, recommended = 20) {
                testSize = it; settings.testSize = it
            }
        },
        WizardStep(
            "⑥ 저녁 알림",
            "오늘 목표를 아직 못 채웠을 때 휴대폰에 알림을 띄울 시간을 정하는 화면이에요.",
            "고른 시간까지 목표를 못 채우면 '오늘 공부를 마저 해요' 알림이 떠요.",
            "오후 8시: 학원·숙제가 끝난 뒤라 하기 좋아요.",
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to "끔", 18 to "오후 6시", 19 to "7시", 20 to "8시", 21 to "9시", 22 to "10시").forEach { (h, label) ->
                    FilterChip(
                        selected = reminderHour == h,
                        onClick = { reminderHour = h; settings.reminderHour = h; Reminder.schedule(context) },
                        label = { Text(if (h == 20) "⭐ $label" else label) },
                    )
                }
            }
        },
        WizardStep(
            "⑦ 카드 보기",
            "카드의 글자 크기와 소리를 정하는 화면이에요.",
            "글자가 작아 보이면 '크게'를 고르세요. 발음 자동 재생을 켜면 카드가 나올 때 발음을 들려줘요.",
            "글자 보통, 발음 자동 재생 켜기, 효과음 켜기",
        ) {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.85f to "작게", 1f to "보통", 1.2f to "크게").forEach { (v, label) ->
                        FilterChip(selected = textScale == v, onClick = { textScale = v; settings.textScale = v }, label = { Text(if (v == 1f) "⭐ $label" else label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("발음 자동 재생", Modifier.weight(1f))
                    Switch(checked = autoSpeak, onCheckedChange = { autoSpeak = it; settings.autoSpeak = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("효과음", Modifier.weight(1f))
                    Switch(checked = soundEffects, onCheckedChange = { soundEffects = it; settings.soundEffects = it })
                }
            }
        },
        WizardStep(
            "⑧ 부모님 비밀번호",
            "아이가 설정과 단어를 마음대로 바꾸지 못하게 4자리 비밀번호를 거는 화면이에요.",
            "비밀번호를 걸면 설정을 열거나 단어를 고칠 때 비밀번호를 물어봐요.",
            "걸어 두기: 아이가 목표나 단어를 임의로 바꾸지 못해요.",
        ) {
            if (settings.hasPin) {
                Text("✅ 비밀번호가 이미 걸려 있어요", color = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalButton(onClick = onSetPin) { Text("🔒 비밀번호 정하기") }
            }
        },
        WizardStep(
            "다 됐어요!",
            "기본 설정을 모두 마쳤어요.",
            "텔레그램으로 학습 결과를 받으려면 설정 > 👨‍👩‍👧 부모님 > 텔레그램 알림에서 연결하세요. " +
                "카드 반복, 종합단어장 같은 세부 설정은 맨 아래 '고급 설정'에 있어요.",
        ) {},
    )
    val step = steps[page]

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column {
                Text("🧭 ${step.title}")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { page / steps.lastIndex.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(step.what, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(step.how, style = MaterialTheme.typography.bodyMedium)
                    step.recommend?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("⭐ 추천: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                step.content()
            }
        },
        confirmButton = {
            TextButton(onClick = { if (page < steps.lastIndex) page++ else onClose() }) {
                Text(
                    when (page) {
                        0 -> "시작"
                        steps.lastIndex -> "완료"
                        else -> "다음 (${page}/${steps.size - 2})"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (page > 0) page-- else onClose() }) { Text(if (page > 0) "이전" else "닫기") }
        },
    )
}
