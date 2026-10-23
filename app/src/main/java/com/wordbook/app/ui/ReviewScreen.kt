package com.wordbook.app.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.DailyStats
import com.wordbook.app.data.Word
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val stats = DailyStats(app)

    val words = dao.observeAllWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var days by mutableStateOf(listOf<Pair<String, Long>>())
        private set
    var streak by mutableStateOf(0)
        private set
    var accuracy by mutableStateOf<Int?>(null)
        private set

    fun refresh() {
        days = stats.lastDays(7)
        streak = stats.streak
        accuracy = stats.totalAnswered.takeIf { it > 0 }?.let { stats.totalRight * 100 / it }
    }
}

/** 복습·통계 tab: today's reviews, studying all decks together, and progress numbers. */
@Composable
fun ReviewScreen(
    onReview: () -> Unit,
    onStudyAll: () -> Unit,
    vm: ReviewViewModel = viewModel(),
) {
    val words by vm.words.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    val now = remember { System.currentTimeMillis() }
    val active = words.filter { !it.excluded }
    val due = active.count { it.box > 0 && it.dueAt <= now }
    val nextDue = active.filter { it.box > 0 && it.dueAt > now }.minOfOrNull { it.dueAt }

    Scaffold(
        topBar = { AppTopBar(title = { Text("복습·통계", style = MaterialTheme.typography.headlineMedium) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Panel {
                    if (due > 0) {
                        Text("오늘 복습 ${due}개", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "잊어버리기 전에 다시 볼 단어예요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("복습 시작", onReview)
                    } else {
                        Text("오늘 복습은 끝났어요 👍", style = MaterialTheme.typography.titleLarge)
                        Text(
                            nextDue?.let { "다음 복습: " + SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(it)) }
                                ?: "단어를 학습하면 복습 일정이 잡혀요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (active.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("모든 단어장 합쳐서 학습", onStudyAll)
                    }
                }
            }
            item {
                Panel {
                    Text("내 기록", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Stat("연속 학습", "${vm.streak}일", Modifier.weight(1f))
                        Stat("전체 단어", "${active.size}개", Modifier.weight(1f))
                        Stat("정답률", vm.accuracy?.let { "$it%" } ?: "-", Modifier.weight(1f))
                    }
                }
            }
            item {
                Panel {
                    Text("최근 7일 학습 시간", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    WeekChart(vm.days)
                }
            }
            item {
                Panel {
                    Text("단어 단계", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    val total = active.size.coerceAtLeast(1)
                    StageRow("새 단어", active.count { it.box == 0 && !it.mastered }, total)
                    StageRow("배우는 중 (1~2단계)", active.count { it.box in 1..2 && !it.mastered }, total)
                    StageRow("익숙해요 (3단계 이상)", active.count { it.box >= 3 && !it.mastered }, total)
                    StageRow("외웠어요", active.count { it.mastered }, total)
                    val excluded = words.size - active.size
                    if (excluded > 0) {
                        Text(
                            "학습에서 뺀 단어 ${excluded}개",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            val hard = active.filter { it.wrongCount > 0 }.sortedByDescending { it.wrongCount }.take(10)
            if (hard.isNotEmpty()) {
                item {
                    Panel {
                        Text("자주 틀린 단어", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        hard.forEach { HardWordRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(20.dp), content = content) }
}

@Composable
private fun WeekChart(days: List<Pair<String, Long>>) {
    val max = days.maxOfOrNull { it.second }?.coerceAtLeast(60_000L) ?: 60_000L
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { i, (date, ms) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (ms > 0) "${(ms + 59_999) / 60_000}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight((ms / max.toFloat()).coerceIn(0.03f, 1f))
                            .background(
                                if (i == days.lastIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                RoundedCornerShape(6.dp),
                            ),
                    )
                }
                Text(
                    if (i == days.lastIndex) "오늘" else date.substring(8).trimStart('0'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
    Text(
        "막대 위 숫자는 분이에요",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StageRow(label: String, count: Int, total: Int) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${count}개", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        ThickProgress(count / total.toFloat(), Modifier.fillMaxWidth())
    }
}

@Composable
private fun HardWordRow(word: Word) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(word.text, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosBadge(word.pos, Modifier.padding(end = 6.dp))
                Text(word.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Text("${word.wrongCount}번 틀림", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
