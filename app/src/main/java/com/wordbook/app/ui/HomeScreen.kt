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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.Deck
import com.wordbook.app.data.DeckSummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()

    val decks = dao.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPhoto: (Uri) -> Unit,
    onOpenDeck: (Long) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val decks by vm.decks.collectAsStateWithLifecycle()
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showNewDeck by rememberSaveable { mutableStateOf(false) }
    var deckToDelete by remember { mutableStateOf<DeckSummary?>(null) }

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
            TopAppBar(
                title = { Text("내 단어장") },
                actions = {
                    if (decks.isNotEmpty()) {
                        TextButton(onClick = { editing = !editing }) { Text(if (editing) "완료" else "편집") }
                    }
                    IconButton(onClick = { showNewDeck = true }) {
                        Icon(Icons.Default.Add, contentDescription = "빈 단어장 만들기")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Default.PhotoLibrary, contentDescription = "앨범에서 선택") }
                ExtendedFloatingActionButton(
                    onClick = {
                        val uri = newPhotoUri(context)
                        pendingUri = uri.toString()
                        takePicture.launch(uri)
                    },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    text = { Text("사진 찍기") },
                )
            }
        },
    ) { padding ->
        if (decks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "영어가 있는 책이나 문서를 찍으면\n단어를 찾아 단어장을 만들어 드려요.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(decks, key = { it.id }) { deck ->
                    DeckCard(deck, editing, onDelete = { deckToDelete = deck }) { onOpenDeck(deck.id) }
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

@Composable
private fun DeckCard(deck: DeckSummary, editing: Boolean, onDelete: () -> Unit, onClick: () -> Unit) {
    val date = remember(deck.createdAt) {
        SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(deck.createdAt))
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
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
                Text("$date · 단어 ${deck.wordCount}개", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                val percent = if (deck.wordCount == 0) 0 else deck.knownCount * 100 / deck.wordCount
                Text("외움 ${deck.knownCount}/${deck.wordCount} · $percent%", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (deck.wordCount == 0) 0f else deck.knownCount / deck.wordCount.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
