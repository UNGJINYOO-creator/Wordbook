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

    /** Re-reads today's numbers; called whenever the home screen is shown. */
    fun refresh() {
        todayMs = stats.studyMs
        goalMs = settings.dailyGoalMinutes * 60_000L
        streak = stats.streak
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPhoto: (List<Uri>) -> Unit,
    onOpenDeck: (Long) -> Unit,
    onToday: () -> Unit,
    onStudyDeck: (Long) -> Unit,
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
                TodayCard(dueCount, minOf(newCount, StudyViewModel.NEW_PER_DAY), vm.todayMs, vm.goalMs, vm.streak, onToday)
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
                        "단어장을 길게 누르면 이름·표지 바꾸기, 삭제를 할 수 있어요",
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
private fun TodayCard(dueCount: Int, newCount: Int, todayMs: Long, goalMs: Long, streak: Int, onToday: () -> Unit) {
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
            Spacer(Modifier.height(14.dp))
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

/** Coloured deck cover: emoji, name, progress, a 학습 button; long press for the menu. */
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
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(deckEmoji(deck), fontSize = 30.sp, modifier = Modifier.weight(1f))
                if (deck.wordCount > 0) {
                    Text(
                        "학습 ›",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable(onClick = onStudy)
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
private fun CoverDialog(deck: DeckSummary, onDismiss: () -> Unit, onSave: (color: Int, emoji: String) -> Unit) {
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
                DeckEmojis.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { e ->
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 22.sp) }
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
fun PinConfirmDialog(text: String, check: (String) -> Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
                Text("삭제")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
