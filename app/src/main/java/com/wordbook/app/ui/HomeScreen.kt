package com.wordbook.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.DailyStats
import com.wordbook.app.data.Deck
import com.wordbook.app.data.DeckSummary
import com.wordbook.app.data.Game
import com.wordbook.app.data.Levels
import com.wordbook.app.data.WrongNote
import com.wordbook.app.data.StudySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Cover colours for the deck tiles. */
val DeckColors = listOf(
    Color(0xFF3182F6), Color(0xFFF04452), Color(0xFF03B26C), Color(0xFFFFB331),
    Color(0xFF8B5CF6), Color(0xFF14B8C4), Color(0xFFFF7A45), Color(0xFF4E5968),
)

val DeckEmojis = listOf("📘", "📗", "📙", "📕", "⭐", "🏃", "🍎", "🌏", "✏️", "🎯", "🐶", "🚀")

/** Stored colour is 1-based; 0 means "pick one from the id". */
fun deckColorIndex(deck: DeckSummary): Int =
    if (deck.color in 1..DeckColors.size) deck.color - 1 else (deck.id % DeckColors.size).toInt()

fun deckEmoji(deck: DeckSummary): String = deck.emoji.ifEmpty { DeckEmojis[(deck.id % DeckEmojis.size).toInt()] }

enum class DeckSort(val label: String) { RECENT("최근 학습순"), NAME("이름순"), PROGRESS("진도 낮은순") }

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val stats = DailyStats(app)
    val settings = StudySettings(app)
    private val prefs = app.getSharedPreferences("home", Context.MODE_PRIVATE)

    val sort = MutableStateFlow(runCatching { DeckSort.valueOf(prefs.getString("sort", null)!!) }.getOrDefault(DeckSort.RECENT))

    val decks = combine(dao.observeDecks(), sort) { list, s ->
        when (s) {
            DeckSort.RECENT -> list.sortedByDescending { maxOf(it.lastStudiedAt, it.createdAt) }
            DeckSort.NAME -> list.sortedBy { it.name.lowercase() }
            DeckSort.PROGRESS -> list.sortedBy { if (it.wordCount == 0) 0f else it.knownCount / it.wordCount.toFloat() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Counted when the home screen opens; good enough since reviews come due in whole days.
    val dueCount = dao.observeDueCount(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val newCount = dao.observeNewCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var todayMs by mutableLongStateOf(0L)
        private set
    var goalMs by mutableLongStateOf(0L)
        private set
    var streak by mutableIntStateOf(0)
        private set
    var learned by mutableIntStateOf(0)
        private set
    var totalMs by mutableLongStateOf(0L)
        private set
    var totalLearned by mutableIntStateOf(0)
        private set
    var wrongCount by mutableIntStateOf(0)
        private set
    private val fishing = com.wordbook.app.data.Fishing(app)
    /** What the 대왕 물고기 card says today. */
    var fishLine by mutableStateOf("")
        private set
    var fishOpen by mutableStateOf(false)
        private set
    private val wrongNote = WrongNote(app)
    var goalWords by mutableIntStateOf(0)
        private set
    private val game = Game(app)
    var stars by mutableIntStateOf(0)
        private set
    var earned by mutableIntStateOf(0)
        private set

    /** Re-reads today's numbers; called whenever the home screen is shown. */
    fun refresh() {
        todayMs = stats.studyMs
        goalMs = settings.dailyGoalMinutes * 60_000L
        streak = stats.streak
        learned = stats.learnedCount
        totalMs = stats.totalStudyMs
        totalLearned = stats.totalLearned
        wrongCount = wrongNote.ids.size
        val timeLeft = settings.dailyGoalMinutes > 0 && stats.studyMs < settings.dailyGoalMinutes * 60_000L
        val wordsLeft = settings.dailyGoalMinutes <= 0 && settings.dailyWords > 0 && stats.learnedCount < settings.dailyWords
        fishOpen = !fishing.caughtToday && !timeLeft && !wordsLeft && fishing.triesLeft > 0
        fishLine = when {
            fishing.caughtToday -> "오늘 ${fishing.caughtFishToday} 낚았어요! 오늘 공부 끝 🎣"
            timeLeft || wordsLeft -> "🔒 오늘 목표를 채우면 열려요"
            fishing.triesLeft <= 0 -> "오늘 도전 끝! 내일 다시 도전해요"
            else -> "열렸어요! 도전 ${fishing.triesLeft}번 남았어요"
        }
        goalWords = settings.dailyWords
        stars = game.stars
        earned = game.earned
    }

    fun setSort(s: DeckSort) {
        sort.value = s
        prefs.edit().putString("sort", s.name).apply()
    }

    fun createEmptyDeck(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(dao.insertDeck(Deck(name = name)))
    }

    fun deleteDeck(id: Long) = viewModelScope.launch {
        dao.unlinkChildren(id)
        dao.deleteDeck(id)
    }

    val collections = dao.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun link(deckId: Long, collectionId: Long) = viewModelScope.launch { dao.setCollection(deckId, collectionId) }

    fun createCollectionAndLink(deckId: Long, name: String) = viewModelScope.launch {
        dao.setCollection(deckId, dao.insertDeck(Deck(name = name, isCollection = true)))
    }

    fun createCollection(name: String) = viewModelScope.launch { dao.insertDeck(Deck(name = name, isCollection = true)) }

    fun updateDeck(id: Long, transform: (Deck) -> Deck) = viewModelScope.launch {
        dao.getDeck(id)?.let { dao.updateDeck(transform(it)) }
    }
}

internal fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

internal fun minutesText(ms: Long): String = "${ms / 60_000}분"

/** "3시간 12분", or "12분" under an hour. */
internal fun hoursText(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
}

/** Explains levels, what they unlock and what stars are for. */
@Composable
private fun LevelDialog(earned: Int, stars: Int, totalMs: Long, totalLearned: Int, onDismiss: () -> Unit) {
    val level = Levels.levelOf(earned)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Lv.$level ${Levels.title(level)}")
                Text(
                    "[${Levels.grade(level)}] ${Levels.description(level)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📊 지금까지 ${hoursText(totalMs)} 공부했고, 단어 ${totalLearned}개를 외웠어요.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("⭐ 별은 뭐예요?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "객관식·빈칸·듣고 쓰기에서 맞히면 ⭐1개, 5문제 연속으로 맞히면 ⭐2개를 더 받아요. " +
                        "7일 연속 출석하면 🏅 배지와 ⭐20개! 모은 별은 복습·통계 탭에서 부모님이 정한 보상 쿠폰으로 바꿀 수 있어요. " +
                        "지금 쓸 수 있는 별은 ⭐$stars 개예요.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                Text("🏆 레벨", style = MaterialTheme.typography.titleSmall)
                Text(
                    "지금까지 받은 별이 쌓이면 레벨이 올라가요. 별을 쿠폰으로 써도 레벨은 내려가지 않아요. " +
                        "레벨이 오르면 칭호가 바뀌고 단어장 표지에 쓸 새 이모지가 열려요.",
                    style = MaterialTheme.typography.bodySmall,
                )
                (1..Levels.titles.size).forEach { n ->
                    val reached = n <= level
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (n == level) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (reached) "✅" else "🔒", fontSize = 14.sp)
                        Column(Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(
                                "Lv.$n [${Levels.grade(n)}] ${Levels.title(n)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (n == level) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(Levels.description(n), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(
                            (Levels.unlocks[n]?.joinToString("") ?: "") + "  ⭐${Levels.starsFor(n)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                Text(
                    "Lv.${Levels.titles.size} 다음부터는 심해의 은둔 고수로 레벨 숫자가 계속 올라가요. 오늘도 화이팅! 🎣",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("좋아요") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPhoto: (List<Uri>) -> Unit,
    onOpenDeck: (Long) -> Unit,
    onToday: () -> Unit,
    onStudyDeck: (Long) -> Unit,
    onWrongNote: () -> Unit,
    onFishing: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val decks by vm.decks.collectAsStateWithLifecycle()
    val dueCount by vm.dueCount.collectAsStateWithLifecycle()
    val newCount by vm.newCount.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showNewDeck by rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var deckToDelete by remember { mutableStateOf<DeckSummary?>(null) }
    var deckToRename by remember { mutableStateOf<DeckSummary?>(null) }
    var deckToStyle by remember { mutableStateOf<DeckSummary?>(null) }
    var deckToLink by remember { mutableStateOf<DeckSummary?>(null) }
    var showNewCollection by rememberSaveable { mutableStateOf(false) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) onPhoto(listOf(Uri.parse(uri)))
        pendingUri = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) onPhoto(uris)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("내 단어장", style = MaterialTheme.typography.headlineMedium) },
                actions = { TextButton(onClick = { showAdd = true }) { Text("＋ 단어 추가") } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "today", span = { GridItemSpan(maxLineSpan) }) {
                TodayCard(dueCount, minOf(newCount, StudyViewModel.newPerDay(vm.settings)), vm.todayMs, vm.goalMs, vm.streak, vm.stars, vm.earned, vm.learned, vm.goalWords, vm.totalMs, vm.totalLearned, onToday)
            }
            item(key = "fishing", span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    onClick = onFishing,
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = if (vm.fishOpen) Color(0xFF1E5AA8) else Color(0xFFDCEBFA)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val ink = if (vm.fishOpen) Color.White else Color(0xFF0B2A5B)
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🐋", fontSize = 28.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("오늘의 끝판왕 · 대왕 물고기 낚시", style = MaterialTheme.typography.titleMedium, color = ink)
                            Text(vm.fishLine, style = MaterialTheme.typography.bodySmall, color = ink)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (vm.wrongCount > 0) {
                item(key = "wrong", span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        onClick = onWrongNote,
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0D6)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 28.sp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("오답노트 ${vm.wrongCount}개", style = MaterialTheme.typography.titleMedium, color = Color(0xFF8A2A0A))
                                Text(
                                    "2번 틀린 단어가 모여요 · 3번 연속 맞히면 빠져요",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8A2A0A),
                                )
                            }
                            Text("학습 ›", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A2A0A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (decks.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Card(
                        onClick = { showAdd = true },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("사진으로 첫 단어장 만들기", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "영어가 있는 책이나 화면을 찍으면 단어를 찾아 드려요",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            } else {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "단어장 ${decks.size}개",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            TextButton(onClick = { sortOpen = true }) { Text("${sort.label} ▾") }
                            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                DeckSort.entries.forEach { s ->
                                    DropdownMenuItem(text = { Text(s.label) }, onClick = { sortOpen = false; vm.setSort(s) })
                                }
                            }
                        }
                    }
                }
                items(decks, key = { it.id }) { deck ->
                    DeckTile(
                        deck = deck,
                        onOpen = { onOpenDeck(deck.id) },
                        onStudy = { onStudyDeck(deck.id) },
                        onRename = { deckToRename = deck },
                        onStyle = { deckToStyle = deck },
                        onLink = { deckToLink = deck },
                        collectionName = collections.firstOrNull { it.id == deck.collectionId }?.name,
                        onDelete = { deckToDelete = deck },
                    )
                }
                item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "단어장을 누르면 바로 학습해요 · 목록은 오른쪽 위 '목록' · 길게 누르면 이름·표지 바꾸기, 삭제",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "단어 추가",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SheetItem(Icons.Default.CameraAlt, "사진 찍기", "책이나 프린트를 바로 찍어요") {
                    showAdd = false
                    val uri = newPhotoUri(context)
                    pendingUri = uri.toString()
                    takePicture.launch(uri)
                }
                SheetItem(Icons.Default.PhotoLibrary, "앨범에서 고르기", "사진이나 스크린샷, 여러 장 한 번에 골라도 돼요") {
                    showAdd = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetItem(Icons.Default.CreateNewFolder, "빈 단어장 만들기", "단어를 직접 입력할게요") {
                    showAdd = false
                    showNewDeck = true
                }
                SheetItem(Icons.Default.CollectionsBookmark, "종합단어장 만들기", "여러 단어장을 묶어서 한꺼번에 학습해요") {
                    showAdd = false
                    showNewCollection = true
                }
            }
        }
    }
    if (showNewDeck) {
        TextInputDialog(
            title = "새 단어장",
            label = "이름",
            initial = "",
            confirmText = "만들기",
            onDismiss = { showNewDeck = false },
            onConfirm = { name ->
                showNewDeck = false
                vm.createEmptyDeck(name.ifBlank { "새 단어장" }, onOpenDeck)
            },
        )
    }
    if (showNewCollection) {
        TextInputDialog(
            title = "새 종합단어장",
            label = "이름",
            initial = "종합 단어장",
            confirmText = "만들기",
            onDismiss = { showNewCollection = false },
            onConfirm = { name ->
                showNewCollection = false
                vm.createCollection(name.ifBlank { "종합 단어장" })
            },
        )
    }
    deckToLink?.let { deck ->
        CollectionDialog(
            title = "종합단어장에 넣기",
            collections = collections,
            current = deck.collectionId,
            onPick = { c -> vm.link(deck.id, c); deckToLink = null },
            onCreate = { name -> vm.createCollectionAndLink(deck.id, name); deckToLink = null },
            onRemove = if (deck.collectionId > 0) ({ vm.link(deck.id, 0); deckToLink = null }) else null,
            onDismiss = { deckToLink = null },
            dismissText = "취소",
        )
    }
    deckToRename?.let { deck ->
        TextInputDialog(
            title = "이름 바꾸기",
            label = "단어장 이름",
            initial = deck.name,
            confirmText = "저장",
            onDismiss = { deckToRename = null },
            onConfirm = { name ->
                if (name.isNotBlank()) vm.updateDeck(deck.id) { it.copy(name = name) }
                deckToRename = null
            },
        )
    }
    deckToStyle?.let { deck ->
        CoverDialog(
            deck = deck,
            level = Levels.levelOf(vm.earned),
            onDismiss = { deckToStyle = null },
            onSave = { color, emoji ->
                vm.updateDeck(deck.id) { it.copy(color = color, emoji = emoji) }
                deckToStyle = null
            },
        )
    }
    deckToDelete?.let { deck ->
        // With a settings password, deleting a deck asks for it too, so a child can't delete one by accident.
        if (vm.settings.hasPin) {
            PinConfirmDialog(
                text = "'${deck.name}' 단어장과 단어 ${deck.wordCount}개를 삭제하려면 비밀번호를 입력하세요.",
                check = vm.settings::checkPin,
                onDismiss = { deckToDelete = null },
                onConfirm = { vm.deleteDeck(deck.id); deckToDelete = null },
            )
        } else {
            ConfirmDialog(
                text = if (deck.isCollection) "'${deck.name}' 종합단어장을 삭제할까요? 묶여 있던 단어장과 단어는 그대로 남아요."
                else "'${deck.name}' 단어장과 단어 ${deck.wordCount}개를 삭제할까요?",
                onDismiss = { deckToDelete = null },
                onConfirm = { vm.deleteDeck(deck.id); deckToDelete = null },
            )
        }
    }
}

/** One row of a bottom sheet menu: icon, title and a short explanation. */
@Composable
fun SheetItem(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(desc, color = MaterialTheme.colorScheme.outline) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TodayCard(
    dueCount: Int,
    newCount: Int,
    todayMs: Long,
    goalMs: Long,
    streak: Int,
    stars: Int,
    earned: Int,
    learned: Int,
    goalWords: Int,
    totalMs: Long,
    totalLearned: Int,
    onToday: () -> Unit,
) {
    var showLevels by remember { mutableStateOf(false) }
    if (showLevels) LevelDialog(earned, stars, totalMs, totalLearned) { showLevels = false }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    val progress = if (goalMs > 0) (todayMs / goalMs.toFloat()).coerceAtMost(1f) else 0f
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 7.dp,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        gapSize = 0.dp,
                    )
                    Text(
                        if (goalMs > 0) "${(progress * 100).toInt()}%" else "🔥$streak",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        "오늘 " + minutesText(todayMs) + if (goalMs > 0) " / ${minutesText(goalMs)}" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "복습 ${dueCount}개 · 새 단어 ${newCount}개 · 🔥 ${streak}일 연속",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (goalWords > 0) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠 오늘 외운 단어", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (learned >= goalWords) "$learned / $goalWords 달성 🎉" else "$learned / $goalWords",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ThickProgress((learned / goalWords.toFloat()).coerceAtMost(1f), Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            // Level bar: stars earned toward the next level.
            val level = Levels.levelOf(earned)
            val from = Levels.starsFor(level)
            val to = Levels.starsFor(level + 1)
            // Tapping the level part explains levels and stars.
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { showLevels = true }.padding(vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lv.$level ${Levels.title(level)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(" ⓘ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.weight(1f))
                    Text("⭐ $stars", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                ThickProgress((earned - from) / (to - from).toFloat(), Modifier.fillMaxWidth())
                Text(
                    "다음 레벨까지 ⭐ ${to - earned} · 누르면 레벨 설명",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "📊 지금까지 ${hoursText(totalMs)} 공부 · 외운 단어 ${totalLearned}개",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (dueCount + newCount > 0) {
                PrimaryButton("오늘 학습 시작", onToday)
            } else {
                Text(
                    "오늘 할 단어를 다 했어요 👍",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

/** Coloured deck cover: tap to study, the 목록 chip opens the word list; long press for the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckTile(
    deck: DeckSummary,
    onOpen: () -> Unit,
    onStudy: () -> Unit,
    onRename: () -> Unit,
    onStyle: () -> Unit,
    onLink: () -> Unit,
    collectionName: String?,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val progress = if (deck.wordCount == 0) 0f else deck.knownCount / deck.wordCount.toFloat()
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DeckColors[deckColorIndex(deck)])
                .combinedClickable(onClick = if (deck.wordCount > 0) onStudy else onOpen, onLongClick = { menu = true })
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(deckEmoji(deck), fontSize = 30.sp, modifier = Modifier.weight(1f))
                if (deck.wordCount > 0) {
                    Text(
                        "목록 ›",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                deck.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (deck.isCollection) "📚 종합 · " else "") + "${deck.wordCount}개 · ${(progress * 100).toInt()}%" +
                    (collectionName?.let { " · $it" } ?: ""),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.35f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("단어 목록·편집") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("이름 바꾸기") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("표지 바꾸기") }, leadingIcon = { Icon(Icons.Default.Palette, null) }, onClick = { menu = false; onStyle() })
            if (!deck.isCollection) {
                DropdownMenuItem(
                    text = { Text(if (deck.collectionId > 0) "종합단어장 바꾸기·빼기" else "종합단어장에 넣기") },
                    leadingIcon = { Icon(Icons.Default.CollectionsBookmark, null) },
                    onClick = { menu = false; onLink() },
                )
            }
            DropdownMenuItem(
                text = { Text("단어장 삭제", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/** Picks a cover colour and emoji; the colour is saved 1-based. */
@Composable
private fun CoverDialog(deck: DeckSummary, level: Int, onDismiss: () -> Unit, onSave: (color: Int, emoji: String) -> Unit) {
    var color by remember { mutableIntStateOf(deckColorIndex(deck)) }
    var emoji by remember { mutableStateOf(deckEmoji(deck)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("표지 바꾸기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(DeckColors[color]),
                    contentAlignment = Alignment.Center,
                ) { Text("$emoji  ${deck.name}", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
                Text("색", style = MaterialTheme.typography.labelLarge)
                DeckColors.chunked(4).forEachIndexed { row, colors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.forEachIndexed { i, c ->
                            val index = row * 4 + i
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(c).clickable { color = index },
                                contentAlignment = Alignment.Center,
                            ) { if (index == color) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Text("이모지", style = MaterialTheme.typography.labelLarge)
                // Emojis from higher levels show locked until the level is reached.
                (DeckEmojis + Levels.unlocks.values.flatten()).chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { e ->
                            val need = Levels.levelFor(e)
                            val open = level >= need
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable(enabled = open) { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(e, fontSize = 22.sp, modifier = Modifier.alpha(if (open) 1f else 0.25f))
                                if (!open) Text("Lv$need", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(color + 1, emoji) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** Asks for the settings password before a step that can't be undone. */
@Composable
fun PinConfirmDialog(
    text: String,
    check: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "삭제",
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(4); error = false },
                    label = { Text("비밀번호 4자리") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (error) Text("비밀번호가 달라요", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (check(pin)) onConfirm() else { error = true; pin = "" } }, enabled = pin.length == 4) {
                Text(confirmText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
