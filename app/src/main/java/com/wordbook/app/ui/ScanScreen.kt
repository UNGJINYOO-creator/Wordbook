package com.wordbook.app.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.Deck
import com.wordbook.app.data.Word
import androidx.room.withTransaction
import com.wordbook.app.ml.EnKoTranslator
import com.wordbook.app.ml.MeaningCheck
import com.wordbook.app.ml.WordExtractor
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Candidate(val text: String, meaning: String, pos: String, selected: Boolean, val alreadySaved: Boolean) {
    var meaning by mutableStateOf(meaning)
    var pos by mutableStateOf(pos)
    var selected by mutableStateOf(selected)
    /** Where the current meaning came from, shown under the word. */
    var source by mutableStateOf(if (meaning.isBlank()) "" else "사진")
    /** Alternative meaning offered on a button: the translation, or the photo's text if it was replaced. */
    var suggestion by mutableStateOf<String?>(null)
    var suggestionSource by mutableStateOf("")
    /** Opened and confirmed by the user, so no longer highlighted. */
    var reviewed by mutableStateOf(false)
    /** Shown in yellow: the meaning was replaced, has an alternative, or is missing. */
    val needsCheck: Boolean get() = !reviewed && (suggestion != null || source.startsWith("번역") || meaning.isBlank())
}

class ScanViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.wordDao()
    /** One or more photos, one address per line. */
    private val uris = checkNotNull(handle.get<String>("uri")).split('\n').filter { it.isNotBlank() }.map(Uri::parse)
    /** Recognised text of every photo, for "인식 원문 보기". */
    var rawLines by mutableStateOf(listOf<com.wordbook.app.ml.OcrLine>())
        private set
    /** -1 creates a new deck; otherwise words are added to this deck. */
    val targetDeckId: Long = handle.get<Long>("deckId") ?: -1L

    var status by mutableStateOf("사진에서 영어 단어를 찾는 중…")
        private set
    var busy by mutableStateOf(true)
        private set
    var deckName by mutableStateOf(
        SimpleDateFormat("M월 d일 HH:mm 단어장", Locale.KOREA).format(Date())
    )
    val candidates = mutableStateListOf<Candidate>()

    init {
        viewModelScope.launch { run() }
    }

    private suspend fun run() {
        // Several photos are read one by one; a word found on more than one keeps its first meaning.
        val extracted = mutableListOf<com.wordbook.app.ml.ExtractedWord>()
        val lines = mutableListOf<com.wordbook.app.ml.OcrLine>()
        var failed = 0
        uris.forEachIndexed { i, uri ->
            if (uris.size > 1) status = "사진 ${i + 1}/${uris.size}에서 영어 단어를 찾는 중…"
            try {
                extracted += WordExtractor.extract(getApplication(), uri)
                lines += WordExtractor.lastLines
            } catch (e: Exception) {
                failed++
            }
        }
        rawLines = lines
        val words = extracted.distinctBy { it.text.lowercase() }
        if (failed == uris.size) {
            busy = false
            status = "사진을 읽지 못했어요."
            return
        }
        if (words.isEmpty()) {
            busy = false
            status = "영어 단어를 찾지 못했어요. 글자가 잘 보이게 다시 찍어 보세요."
            return
        }
        // A photo makes its own new deck, so every word goes in even if another deck has it.
        // Adding to an existing deck leaves out only the words that deck already has.
        val saved = if (targetDeckId >= 0) dao.getWords(targetDeckId).map { it.text.lowercase() }.toSet() else emptySet()
        candidates.addAll(words.map {
            Candidate(it.text, it.meaning, it.pos, selected = it.text !in saved, alreadySaved = it.text in saved)
        })
        val fromPhoto = candidates.count { !MeaningCheck.suspicious(it.meaning) }
        val found = "단어 ${candidates.size}개 (사진 속 뜻 ${fromPhoto}개)"

        status = "번역 모델 준비 중… (처음 한 번만 다운로드해요)"
        try {
            EnKoTranslator.ensureModel()
        } catch (e: Exception) {
            busy = false
            status = "$found. 번역 모델을 받지 못해 비어 있는 뜻은 직접 입력해 주세요."
            candidates.filter { MeaningCheck.looksGarbled(it.meaning) }.forEach { it.meaning = ""; it.source = "" }
            return
        }
        // Second check on every word: a broken photo meaning is replaced by the translation
        // (the photo text stays available on a button); a clean one is kept, with the
        // translation offered when the two don't share any syllable.
        candidates.forEachIndexed { i, c ->
            status = "뜻 확인 중… ${i + 1}/${candidates.size}"
            // Translate as the same part of speech as the book (connect -> 연결하다, not 연결).
            val translated = runCatching { EnKoTranslator.translateAs(c.text, c.pos) }.getOrDefault("")
            if (MeaningCheck.suspicious(c.meaning)) {
                if (translated.isNotBlank()) {
                    if (c.meaning.isNotBlank()) { c.suggestion = c.meaning; c.suggestionSource = "사진" }
                    c.meaning = translated
                    c.source = "번역 (사진 뜻이 이상해서)"
                }
            } else if (translated.isNotBlank() && MeaningCheck.nearMiss(c.meaning, translated)) {
                // One syllable off from the translation: the photo letter was misread.
                c.suggestion = c.meaning
                c.suggestionSource = "사진"
                c.meaning = translated
                c.source = "번역 (사진 글자가 잘못 읽혀서)"
            } else if (translated.isNotBlank() && MeaningCheck.disagree(c.meaning, translated)) {
                c.suggestion = translated
                c.suggestionSource = "번역"
            }
        }
        busy = false
        status = "찾았어요: $found. 저장할 단어를 고르세요."
    }

    fun setAll(selected: Boolean) = candidates.forEach { it.selected = selected }

    val collections = dao.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Links the new deck into a 종합단어장 (no copies). */
    fun link(deckId: Long, collectionId: Long) = viewModelScope.launch { dao.setCollection(deckId, collectionId) }

    fun createCollectionAndLink(deckId: Long, name: String) = viewModelScope.launch {
        val id = dao.insertDeck(Deck(name = name, isCollection = true))
        dao.setCollection(deckId, id)
    }

    fun save(onSaved: (Long) -> Unit) = viewModelScope.launch {
        val chosen = candidates.filter { it.selected }.map { it to it.meaning.trim() }
        val deckId = if (targetDeckId >= 0) {
            dao.insertWords(chosen.map { (c, m) -> Word(deckId = targetDeckId, text = c.text, meaning = m, pos = c.pos) })
            targetDeckId
        } else {
            db.withTransaction {
                val id = dao.insertDeck(Deck(name = deckName.ifBlank { "새 단어장" }))
                dao.insertWords(chosen.map { (c, m) -> Word(deckId = id, text = c.text, meaning = m, pos = c.pos) })
                id
            }
        }
        onSaved(deckId)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ScanViewModel(this[APPLICATION_KEY]!!, createSavedStateHandle()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onStudy: (deckId: Long) -> Unit,
    onHome: () -> Unit,
    vm: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val selectedCount = vm.candidates.count { it.selected }
    var savedDeckId by remember { mutableStateOf<Long?>(null) }
    // A new deck from photos is offered to a 종합단어장 first, then 바로 학습할까요?
    var askCollection by remember { mutableStateOf<Long?>(null) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    askCollection?.let { id ->
        CollectionDialog(
            title = "종합단어장에 추가할까요?",
            collections = collections,
            current = 0,
            onPick = { c -> vm.link(id, c); askCollection = null; savedDeckId = id },
            onCreate = { name -> vm.createCollectionAndLink(id, name); askCollection = null; savedDeckId = id },
            onRemove = null,
            onDismiss = { askCollection = null; savedDeckId = id },
        )
    }
    var editing by remember { mutableStateOf<Candidate?>(null) }
    editing?.let { c -> CandidateDialog(c, onDismiss = { editing = null }) }
    savedDeckId?.let { id ->
        AlertDialog(
            onDismissRequest = { savedDeckId = null; onHome() },
            title = { Text("단어 ${selectedCount}개를 저장했어요") },
            text = { Text("바로 학습할까요?") },
            confirmButton = { TextButton(onClick = { savedDeckId = null; onStudy(id) }) { Text("예, 학습할래요") } },
            dismissButton = { TextButton(onClick = { savedDeckId = null; onHome() }) { Text("아니오") } },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppTopBar(
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("단어 고르기") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        bottomBar = {
            if (vm.candidates.isNotEmpty()) {
                PrimaryButton(
                    "단어 ${selectedCount}개 저장",
                    { vm.save { id -> if (vm.targetDeckId < 0) askCollection = id else savedDeckId = id } },
                    Modifier.padding(16.dp).navigationBarsPadding(),
                    enabled = selectedCount > 0 && !vm.busy,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                }
                Text(vm.status, style = MaterialTheme.typography.bodyMedium)
            }
            if (vm.candidates.isEmpty()) {
                if (!vm.busy) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onBack) { Text("돌아가기", textAlign = TextAlign.Center) }
                    }
                }
                return@Column
            }
            if (vm.targetDeckId < 0) {
                OutlinedTextField(
                    value = vm.deckName,
                    onValueChange = { vm.deckName = it },
                    label = { Text("단어장 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { vm.setAll(true) }) { Text("전체 선택") }
                TextButton(onClick = { vm.setAll(false) }) { Text("전체 해제") }
            }
            HorizontalDivider()
            var showRaw by remember { mutableStateOf(false) }
            if (vm.candidates.any { it.needsCheck }) {
                Text(
                    "노란 줄은 사진 속 뜻이 깨졌거나 번역과 달라서 확인이 필요한 단어예요. 탭해서 고쳐 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(vm.candidates, key = { _, c -> c.text }) { _, c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (c.needsCheck) CheckYellow else Color.Transparent)
                            .clickable { editing = c }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.selected, onCheckedChange = { c.selected = it })
                        Column(Modifier.weight(1f)) {
                            Text(c.text, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PosBadge(c.pos, Modifier.padding(end = 6.dp))
                                Text(
                                    c.meaning.ifBlank { "(뜻 없음 · 탭해서 입력)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (c.meaning.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            val notes = listOfNotNull(
                                if (c.needsCheck) "확인해 주세요" else null,
                                c.source.takeIf { it.isNotEmpty() }?.let { "뜻: $it" },
                                if (c.alreadySaved) "이 단어장에 이미 있어요" else null,
                            )
                            if (notes.isNotEmpty()) {
                                Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(Icons.Default.Edit, "수정", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
                item {
                    TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.padding(8.dp)) {
                        Text(if (showRaw) "인식 원문 숨기기" else "빠진 단어가 있나요? 인식 원문 보기")
                    }
                }
                if (showRaw) {
                    items(vm.rawLines) { l ->
                        Text(
                            "[${l.height}] ${l.text}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private val CheckYellow = Color(0x33FFC107)

/** Edits one found word: meaning, part of speech, the alternative meaning, and whether to save it. */
@Composable
private fun CandidateDialog(c: Candidate, onDismiss: () -> Unit) {
    var meaning by remember { mutableStateOf(c.meaning) }
    var pos by remember { mutableStateOf(c.pos) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.text)
                PosChips(pos) { pos = it }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.source.isNotEmpty()) {
                    Text("지금 뜻: ${c.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                OutlinedTextField(meaning, { meaning = it }, label = { Text("뜻") }, maxLines = 3)
                c.suggestion?.let { s ->
                    Text("${c.suggestionSource} 뜻", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    SecondaryButton(s, { meaning = s })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (meaning.trim() != c.meaning) {
                    if (c.suggestion == meaning) {
                        // Swapped to the alternative: keep the old one as the alternative.
                        c.suggestion = c.meaning
                        val s = c.suggestionSource
                        c.suggestionSource = if (s == "번역") "사진" else "번역"
                        c.source = s
                    } else {
                        c.source = "직접 입력"
                    }
                    c.meaning = meaning.trim()
                }
                c.pos = pos
                c.selected = true
                c.reviewed = true
                onDismiss()
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = { c.selected = false; c.reviewed = true; onDismiss() }) { Text("저장 안 함") }
        },
    )
}
