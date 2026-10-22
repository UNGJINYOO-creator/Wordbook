package com.wordbook.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.Deck
import com.wordbook.app.data.Word
import com.wordbook.app.ml.EnKoTranslator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeckViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    val deckId: Long = checkNotNull(handle.get<Long>("deckId"))

    val deck = dao.observeDeck(deckId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val words = dao.observeWords(deckId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun meaningOrTranslate(text: String, meaning: String, pos: String): String =
        meaning.ifBlank {
            runCatching { EnKoTranslator.ensureModel(); EnKoTranslator.translateAs(text, pos) }.getOrDefault("")
        }

    fun addWord(text: String, meaning: String, pos: String) = viewModelScope.launch {
        dao.insertWord(Word(deckId = deckId, text = text, meaning = meaningOrTranslate(text, meaning, pos), pos = pos))
    }

    fun updateWord(word: Word, text: String, meaning: String, pos: String) = viewModelScope.launch {
        dao.updateWord(word.copy(text = text, meaning = meaningOrTranslate(text, meaning, pos), pos = pos))
    }

    fun toggleMastered(word: Word) = viewModelScope.launch {
        dao.updateWord(word.copy(mastered = !word.mastered, known = word.known || !word.mastered))
    }

    fun deleteWord(word: Word) = viewModelScope.launch { dao.deleteWord(word) }

    fun updateDeck(transform: (Deck) -> Deck) = viewModelScope.launch {
        deck.value?.let { dao.updateDeck(transform(it)) }
    }

    /** Tab-separated "word<TAB>meaning" lines, for sharing / backup. */
    fun exportText(): String = words.value.joinToString("\n") { "${it.text}\t${it.meaning}" }

    companion object {
        val Factory = viewModelFactory {
            initializer { DeckViewModel(this[APPLICATION_KEY]!!, createSavedStateHandle()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    onBack: () -> Unit,
    onPhoto: (deckId: Long, Uri) -> Unit,
    onStudy: (deckId: Long, onlyUnknown: Boolean) -> Unit,
    vm: DeckViewModel = viewModel(factory = DeckViewModel.Factory),
) {
    val context = LocalContext.current
    val deck by vm.deck.collectAsStateWithLifecycle()
    val words by vm.words.collectAsStateWithLifecycle()
    val unknownCount = words.count { !it.known }
    val studyCount = words.count { !it.mastered }.takeIf { it > 0 } ?: words.size

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Word?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) onPhoto(vm.deckId, Uri.parse(uri))
        pendingUri = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(vm.deckId, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck?.name ?: "", modifier = Modifier.clickable { showRename = true }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, deck?.name)
                            putExtra(Intent.EXTRA_TEXT, vm.exportText())
                        }
                        context.startActivity(Intent.createChooser(send, "단어장 내보내기"))
                    }, enabled = words.isNotEmpty()) { Icon(Icons.Default.Share, "내보내기") }
                    IconButton(onClick = { showOptions = true }) { Icon(Icons.Default.Settings, "단어장 옵션") }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Default.PhotoLibrary, "앨범 사진에서 단어 추가") }
                SmallFloatingActionButton(onClick = {
                    val uri = newPhotoUri(context)
                    pendingUri = uri.toString()
                    takePicture.launch(uri)
                }) { Icon(Icons.Default.CameraAlt, "사진 찍어 단어 추가") }
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "카드 추가") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 200.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onStudy(vm.deckId, false) },
                        enabled = words.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("학습 ($studyCount)") }
                    OutlinedButton(
                        onClick = { onStudy(vm.deckId, true) },
                        enabled = unknownCount > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("모르는 것만 ($unknownCount)") }
                }
                HorizontalDivider()
            }
            if (words.isEmpty()) {
                item {
                    Text(
                        "카드가 없어요. + 로 직접 추가하거나 사진으로 단어를 넣어 보세요.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(words, key = { it.id }) { word ->
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(word.text)
                            PosBadge(word.pos, Modifier.padding(start = 6.dp))
                        }
                    },
                    supportingContent = { Text(word.meaning.ifBlank { "(뜻 없음)" }) },
                    leadingContent = {
                        // Tap to toggle "외웠어요"; mastered cards are skipped in regular study.
                        IconButton(onClick = { vm.toggleMastered(word) }) {
                            Icon(
                                if (word.mastered) Icons.Default.DoneAll else Icons.Default.CheckCircle,
                                contentDescription = when {
                                    word.mastered -> "외웠어요"
                                    word.known -> "헷갈려요"
                                    else -> "아직"
                                },
                                tint = when {
                                    word.mastered -> MaterialTheme.colorScheme.tertiary
                                    word.known -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                            )
                        }
                    },
                    trailingContent = { Icon(Icons.Default.Edit, "수정", tint = MaterialTheme.colorScheme.outline) },
                    modifier = Modifier.clickable { editing = word },
                )
            }
        }
    }

    if (showAdd) {
        CardEditDialog(
            title = "카드 추가",
            initialText = "",
            initialMeaning = "",
            initialPos = "",
            onDismiss = { showAdd = false },
            onConfirm = { t, m, p -> vm.addWord(t, m, p); showAdd = false },
        )
    }
    editing?.let { w ->
        CardEditDialog(
            title = "카드 수정",
            initialText = w.text,
            initialMeaning = w.meaning,
            initialPos = w.pos,
            onDismiss = { editing = null },
            onConfirm = { t, m, p -> vm.updateWord(w, t, m, p); editing = null },
            onDelete = { vm.deleteWord(w); editing = null },
        )
    }
    if (showRename) {
        TextInputDialog(
            title = "이름 바꾸기",
            label = "단어장 이름",
            initial = deck?.name ?: "",
            confirmText = "저장",
            onDismiss = { showRename = false },
            onConfirm = { name ->
                if (name.isNotBlank()) vm.updateDeck { it.copy(name = name) }
                showRename = false
            },
        )
    }
    if (showOptions) {
        deck?.let { d -> DeckOptionsDialog(d, onChange = { vm.updateDeck { _ -> it } }, onDismiss = { showOptions = false }) }
    }
}

@Composable
private fun DeckOptionsDialog(deck: Deck, onChange: (Deck) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("단어장 옵션") },
        text = {
            Column {
                OptionRow("카드 섞기", "학습할 때마다 순서를 무작위로", deck.shuffle) { onChange(deck.copy(shuffle = it)) }
                OptionRow("틀린 카드 반복", "모르는 카드를 다 알 때까지 다시", deck.repeatMissed) { onChange(deck.copy(repeatMissed = it)) }
                OptionRow("뒷면 먼저", "뜻을 보고 영어 단어 맞히기", deck.backFirst) { onChange(deck.copy(backFirst = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun OptionRow(title: String, desc: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
