package com.wordbook.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.FISH_MIX_DEFAULT
import com.wordbook.app.data.Priority
import com.wordbook.app.data.StudySettings
import com.wordbook.app.data.TropicalMode

/** Ready-made 오늘의 낚시 shares: today's words, other 학원 words, built-in words. */
private val FishMixes = listOf(
    FISH_MIX_DEFAULT to "오늘 80 · 학원 20 · 기본 0",
    listOf(60, 30, 10) to "오늘 60 · 학원 30 · 기본 10",
    listOf(40, 40, 20) to "오늘 40 · 학원 40 · 기본 20",
    listOf(100, 0, 0) to "오늘 단어만",
)

fun academySummary(settings: StudySettings): String = listOf(
    if (settings.academyFirst) "학원 단어 먼저" else "섞어서",
    FishMixes.firstOrNull { it.first == settings.fishMix }?.second ?: settings.fishMix.joinToString("·"),
    "열대 ${settings.tropicalMode.label}",
).joinToString(" · ")

/**
 * 📚 학원 단어 우선: which added decks count, and how 오늘 학습, 오늘의 낚시, 대양 원정 and the 열대 바다 use them.
 * Every value is saved at once.
 */
@Composable
fun AcademySettings(settings: StudySettings, onChange: () -> Unit = {}) {
    val context = LocalContext.current
    // Added decks (name → word count), read from the database.
    var decks by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(Unit) {
        val dao = AppDatabase.get(context).wordDao()
        val words = dao.getAllWords()
        val all = dao.getAllDecks()
        val priority = Priority(words, all, emptySet())
        val names = all.associate { it.id to it.name }
        decks = words.filter { priority.added(it) }.groupingBy { names[it.deckId] ?: "" }.eachCount()
            .filterKeys { it.isNotBlank() }.toList().sortedBy { it.first }
    }
    var off by remember { mutableStateOf(settings.priorityOff) }
    var first by remember { mutableStateOf(settings.academyFirst) }
    var fill by remember { mutableStateOf(settings.fillBuiltIn) }
    var max by remember { mutableIntStateOf(settings.academyMax) }
    var mix by remember { mutableStateOf(settings.fishMix) }
    var giant by remember { mutableStateOf(settings.giantAcademy) }
    var tropical by remember { mutableStateOf(settings.tropicalMode) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "직접 추가한 단어장(학원·시험대비 등)이 주 학습 단어예요. 기본 1800단어는 그 뒤에 채워져요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text("우선 단어장", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        if (decks.isEmpty()) {
            Text("아직 추가한 단어장이 없어요", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
        }
        decks.forEach { (name, count) ->
            SwitchRow(name, "${count}단어 · 끄면 기본 단어처럼 뒤로 가요", name !in off) { on ->
                off = if (on) off - name else off + name
                settings.priorityOff = off
                onChange()
            }
        }
        Text("📅 오늘 학습", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        SwitchRow("학원 단어 먼저", "새 단어와 복습 모두 학원 단어를 먼저 보여 줘요", first) { first = it; settings.academyFirst = it; onChange() }
        SwitchRow("모자라면 기본 단어로 채우기", "학원 새 단어가 하루 목표보다 적으면 1800단어에서 채워요", fill) { fill = it; settings.fillBuiltIn = it; onChange() }
        Labeled("하루 최대 학원 새 단어", "추가한 단어는 기본으로 그날 전부 나와요. 한 번에 아주 많이 넣었을 때만 줄여 주세요.") {
            NumberChoice(max, listOf(0 to "전부", 30 to "30개", 50 to "50개", 100 to "100개"), "개", 500, min = 0, recommended = 0) {
                max = it; settings.academyMax = it; onChange()
            }
        }
        Text("🎣 오늘의 낚시", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        Labeled("문제 비율", "오늘 배운 단어 · 예전 학원 단어 · 기본 1800단어") {
            Choice(mix, FishMixes, recommended = FISH_MIX_DEFAULT) { mix = it; settings.fishMix = it; onChange() }
        }
        Text("⛵ 대양 원정", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        SwitchRow("학원 단어도 작살로", "학원 단어를 외우고 오늘의 낚시에 성공하면 지금 물고기의 빈칸이 🟩로 채워지고, 최종전에도 나와요", giant) {
            giant = it; settings.giantAcademy = it; onChange()
        }
        Text("🌴 열대 바다", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
        Labeled("물고기 한 마리가 지키는 단어", tropical.description) {
            Choice(tropical, TropicalMode.entries.map { it to it.label }, recommended = TropicalMode.BATCH) {
                tropical = it; settings.tropicalMode = it; onChange()
            }
        }
    }
}
