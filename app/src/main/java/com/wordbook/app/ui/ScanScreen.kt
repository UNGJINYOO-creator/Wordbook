package com.wordbook.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Candidate(val text: String, meaning: String, selected: Boolean, val alreadySaved: Boolean) {
    var meaning by mutableStateOf(meaning)
    var selected by mutableStateOf(selected)
    /** Where the current meaning came from, shown under the word. */
    var source by mutableStateOf(if (meaning.isBlank()) "" else "사진")
    /** Machine translation offered when it disagrees with the photo's meaning. */
    var suggestion by mutableStateOf<String?>(null)
}

class ScanViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.wordDao()
    private val uri = Uri.parse(checkNotNull(handle.get<String>("uri")))
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
        val words = try {
            WordExtractor.extract(getApplication(), uri)
        } catch (e: Exception) {
            busy = false
            status = "사진을 읽지 못했어요: ${e.message}"
            return
        }
        if (words.isEmpty()) {
            busy = false
            status = "영어 단어를 찾지 못했어요. 글자가 잘 보이게 다시 찍어 보세요."
            return
        }
        val saved = dao.allWordTexts().toSet()
        // Words already in some deck start unchecked so they aren't duplicated by accident.
        candidates.addAll(words.map {
            Candidate(it.text, it.meaning, selected = it.text !in saved, alreadySaved = it.text in saved)
        })
        val fromPhoto = candidates.count { !MeaningCheck.looksGarbled(it.meaning) }
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
        // Check every word: broken or missing photo meanings are replaced by the translation;
        // a clean photo meaning is kept, with the translation offered if the two disagree.
        candidates.forEachIndexed { i, c ->
            status = "뜻 확인 중… ${i + 1}/${candidates.size}"
            val translated = runCatching { EnKoTranslator.translate(c.text) }.getOrDefault("")
            if (MeaningCheck.looksGarbled(c.meaning)) {
                c.meaning = translated
                c.source = if (translated.isBlank()) "" else "번역"
            } else if (translated.isNotBlank() && MeaningCheck.disagree(c.meaning, translated)) {
                c.suggestion = translated
            }
        }
        busy = false
        status = "찾았어요: $found. 저장할 단어를 고르세요."
    }

    fun setAll(selected: Boolean) = candidates.forEach { it.selected = selected }

    fun save(onSaved: (Long) -> Unit) = viewModelScope.launch {
        val chosen = candidates.filter { it.selected }.map { it.text to it.meaning.trim() }
        val deckId = if (targetDeckId >= 0) {
            dao.insertWords(chosen.map { (t, m) -> Word(deckId = targetDeckId, text = t, meaning = m) })
            targetDeckId
        } else {
            db.withTransaction {
                val id = dao.insertDeck(Deck(name = deckName.ifBlank { "새 단어장" }))
                dao.insertWords(chosen.map { (t, m) -> Word(deckId = id, text = t, meaning = m) })
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
    onSaved: (Long) -> Unit,
    vm: ScanViewModel = viewModel(factory = ScanViewModel.Factory),
) {
    val selectedCount = vm.candidates.count { it.selected }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("단어 고르기") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        bottomBar = {
            if (vm.candidates.isNotEmpty()) {
                Button(
                    onClick = { vm.save(onSaved) },
                    enabled = selectedCount > 0 && !vm.busy,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("단어 ${selectedCount}개 저장") }
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
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(vm.candidates, key = { _, c -> c.text }) { _, c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { c.selected = !c.selected }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.selected, onCheckedChange = { c.selected = it })
                        Column(Modifier.weight(1f)) {
                            Text(c.text, style = MaterialTheme.typography.titleMedium)
                            if (c.source.isNotEmpty()) {
                                Text("뜻: ${c.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            if (c.alreadySaved) {
                                Text("이미 저장된 단어", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1.3f)) {
                            OutlinedTextField(
                                value = c.meaning,
                                onValueChange = { c.meaning = it; c.source = "" },
                                placeholder = { Text("뜻") },
                                singleLine = true,
                            )
                            c.suggestion?.let { s ->
                                TextButton(onClick = { c.meaning = s; c.source = "번역"; c.suggestion = null }) {
                                    Text("번역으로 바꾸기: $s", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
