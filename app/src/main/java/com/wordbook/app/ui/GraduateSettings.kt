package com.wordbook.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.StudySettings

fun graduateSummary(settings: StudySettings): String =
    if (!settings.gradOn) "잠깐 꺼 둠"
    else "연속 ${settings.gradStreak}일 · 철자 ${settings.gradSpell}번 · " + if (settings.gradHide) "졸업하면 빼기" else "졸업하면 아주 가끔"

/** 🎓 졸업 단어: when a word is too easy to keep studying, and what happens to it then. Saved at once. */
@Composable
fun GraduateSettings(settings: StudySettings, onChange: () -> Unit = {}) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) { count = AppDatabase.get(context).wordDao().getAllWords().count { it.graduated } }
    var on by remember { mutableStateOf(settings.gradOn) }
    var streak by remember { mutableIntStateOf(settings.gradStreak) }
    var spell by remember { mutableIntStateOf(settings.gradSpell) }
    var hide by remember { mutableStateOf(settings.gradHide) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "너무 쉬워진 단어는 졸업해서 아주 가끔만 확인 문제로 나와요. 한 번이라도 틀리면 졸업이 취소되고 다시 공부해요." +
                if (count >= 0) " 지금 졸업한 단어 ${count}개 (단어 목록의 🎓 졸업에서 볼 수 있어요)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        SwitchRow("졸업 단어 쓰기", "시험 전에는 잠깐 끄면 졸업한 단어도 평소처럼 나와요", on) { on = it; settings.gradOn = it; onChange() }
        Labeled("연속으로 맞혀야 하는 날", "객관식·빈칸·듣고 쓰기 정답을 하루 한 번만 세요. 틀리면 처음부터예요.") {
            NumberChoice(streak, listOf(5 to "5일", 10 to "10일", 15 to "15일", 20 to "20일"), "일", 60, min = 1, recommended = 10) {
                streak = it; settings.gradStreak = it; onChange()
            }
        }
        Labeled("그중 철자를 완벽하게 쓴 날", "듣고 쓰기나 전체 빈칸에서 철자를 다 맞힌 날이에요") {
            NumberChoice(spell, listOf(0 to "필요 없음", 3 to "3번", 5 to "5번"), "번", 30, min = 0, recommended = 5) {
                spell = it; settings.gradSpell = it; onChange()
            }
        }
        Labeled("졸업한 단어는") {
            Choice(hide, listOf(false to "아주 가끔 나오기", true to "아예 빼기"), recommended = false) {
                hide = it; settings.gradHide = it; onChange()
            }
        }
    }
}
