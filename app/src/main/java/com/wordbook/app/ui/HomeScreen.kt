package com.wordbook.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val stats = DailyStats(app)
    private val settings = StudySettings(app)

    val decks = dao.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Counted when the home screen opens; good enough since reviews come due in whole days.
    val dueCount = dao.observeDueCount(System.currentTimeMillis())
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

    fun createEmptyDeck(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(dao.insertDeck(Deck(name = name)))
    }

    fun deleteDeck(id: Long) = viewModelScope.launch { dao.deleteDeck(id) }
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
    onPhoto: (Uri) -> Unit,
    onOpenDeck: (Long) -> Unit,
    onReview: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val decks by vm.decks.collectAsStateWithLifecycle()
    val dueCount by vm.dueCount.collectAsStateWithLifecycle()
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showNewDeck by rememberSaveable { mutableStateOf(false) }
    var deckToDelete by remember { mutableStateOf<DeckSummary?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) onPhoto(Uri.parse(uri))
        pendingUri = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("내 단어장", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    if (decks.isNotEmpty()) {
                        TextButton(onClick = { editing = !editing }) { Text(if (editing) "완료" else "편집") }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "today") {
                TodayCard(dueCount, vm.todayMs, vm.goalMs, vm.streak, onReview)
            }
            item(key = "add") {
                PrimaryButton("📷  사진으로 단어 추가", { showAdd = true })
            }
            if (decks.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "영어가 있는 책이나 화면을 찍으면\n단어를 찾아 단어장을 만들어 드려요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                    )
                }
            } else {
                item(key = "title") {
                    Text(
                        "단어장 ${decks.size}개",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
            }
            items(decks, key = { it.id }) { deck ->
                DeckCard(deck, editing, onDelete = { deckToDelete = deck }) { onOpenDeck(deck.id) }
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
                SheetItem(Icons.Default.PhotoLibrary, "앨범에서 고르기", "찍어 둔 사진이나 스크린샷") {
                    showAdd = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetItem(Icons.Default.CreateNewFolder, "빈 단어장 만들기", "단어를 직접 입력할게요") {
                    showAdd = false
                    showNewDeck = true
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
    deckToDelete?.let { deck ->
        ConfirmDialog(
            text = "'${deck.name}' 단어장과 단어 ${deck.wordCount}개를 삭제할까요?",
            onDismiss = { deckToDelete = null },
            onConfirm = { vm.deleteDeck(deck.id); deckToDelete = null },
        )
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
private fun TodayCard(dueCount: Int, todayMs: Long, goalMs: Long, streak: Int, onReview: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("오늘", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("복습할 단어", "${dueCount}개", Modifier.weight(1f))
                Stat(
                    "학습 시간",
                    minutesText(todayMs) + if (goalMs > 0) " / ${minutesText(goalMs)}" else "",
                    Modifier.weight(1f),
                )
                Stat("연속 학습", "${streak}일", Modifier.weight(1f))
            }
            if (goalMs > 0) {
                Spacer(Modifier.height(12.dp))
                ThickProgress((todayMs / goalMs.toFloat()).coerceAtMost(1f), Modifier.fillMaxWidth())
            }
            if (dueCount > 0) {
                Spacer(Modifier.height(16.dp))
                PrimaryButton("복습 ${dueCount}개 시작", onReview)
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

@Composable
private fun DeckCard(deck: DeckSummary, editing: Boolean, onDelete: () -> Unit, onClick: () -> Unit) {
    val date = remember(deck.createdAt) {
        SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(deck.createdAt))
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (editing) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text("$date · 단어 ${deck.wordCount}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.weight(1f))
                val percent = if (deck.wordCount == 0) 0 else deck.knownCount * 100 / deck.wordCount
                Text("$percent%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(14.dp))
            ThickProgress(
                if (deck.wordCount == 0) 0f else deck.knownCount / deck.wordCount.toFloat(),
                Modifier.fillMaxWidth(),
            )
        }
    }
}
