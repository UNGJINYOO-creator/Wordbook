package com.wordbook.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.withTransaction
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.Deck
import com.wordbook.app.data.Word
import com.wordbook.app.ml.Dictionary
import com.wordbook.app.ml.EnKoTranslator
import com.wordbook.app.ml.PasteImport
import com.wordbook.app.ml.PasteImport.Note
import com.wordbook.app.ml.SpellFix
import com.wordbook.app.ml.WordExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXAMPLE = """# 1과
connect 연결하다
helpful - 도움이 되는
fast (형) 빠른 (부) 빨리
apple

# 2과
1. borrow: 빌리다
lend	빌려주다"""

/** One pasted word on the check screen: which version to save, and whether to save it at all. */
private class PasteRow(val checked: PasteImport.Checked, include: Boolean, val already: Boolean) {
    var include by mutableStateOf(include)
    /** "checked" = after the check, "original" = as pasted, "offer" = the dictionary's meaning. */
    var pick by mutableStateOf("checked")
    var edited by mutableStateOf<PasteImport.PastedWord?>(null)
    val word: PasteImport.PastedWord
        get() = edited ?: when (pick) {
            "original" -> checked.original
            "offer" -> checked.offer ?: checked.word
            else -> checked.word
        }
}

private class PasteDeck(name: String, val existingId: Long?, val rows: SnapshotStateList<PasteRow>) {
    var name by mutableStateOf(name)
}

/**
 * 붙여넣기로 단어 추가: paste a word list (or several, each under its own name line) and save them all at once.
 * Every word goes through the same second check as a photo: 낚아보카 사전 meaning and part of speech, spelling.
 * With [targetDeckId] every word goes into that deck; otherwise each name line makes its own deck (or adds to the
 * deck already called that).
 */
@Composable
fun PasteImportDialog(targetDeckId: Long?, newName: String? = null, onDismiss: () -> Unit, onSaved: (firstDeckId: Long, deckCount: Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val db = remember { AppDatabase.get(context) }
    val dao = remember { db.wordDao() }
    var text by rememberSaveable { mutableStateOf("") }
    var decks by remember { mutableStateOf<List<PasteDeck>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<PasteRow?>(null) }

    fun read() = scope.launch {
        busy = "단어와 뜻을 확인하는 중…"
        val dict = Dictionary.get(context)
        val spell = WordExtractor.dictionary(context)
        val allDecks = dao.getAllDecks().filter { !it.isCollection }
        val target = targetDeckId?.let { id -> allDecks.firstOrNull { it.id == id } }
        val parsed = withContext(Dispatchers.Default) {
            val p = if (newName.isNullOrBlank()) PasteImport.parse(text) else PasteImport.parse(text, newName)
            // Into one deck: the name lines only tidy the paste, every word goes in together.
            if (target != null) listOf(PasteImport.PastedDeck(target.name, p.flatMap { it.words }.distinctBy { it.text.lowercase() })) else p
        }
        val out = parsed.map { d ->
            val existing = target ?: allDecks.firstOrNull { it.name.trim() == d.name.trim() }
            val have = existing?.let { e -> dao.getWords(e.id).map { it.text.trim().lowercase() }.toSet() }.orEmpty()
            val rows = withContext(Dispatchers.Default) {
                d.words.map { w ->
                    val c = PasteImport.check(w, dict, { SpellFix.isWord(spell, it) }, { SpellFix.fix(spell, it) })
                    val already = c.word.text.lowercase() in have
                    PasteRow(c, include = !already, already = already)
                }
            }
            PasteDeck(d.name, existing?.id, mutableStateListOf<PasteRow>().apply { addAll(rows) })
        }
        decks = out
        busy = null
    }

    fun save(list: List<PasteDeck>) = scope.launch {
        busy = "저장하는 중…"
        val chosen = list.map { d -> d to d.rows.filter { it.include }.map { it.word } }.filter { it.second.isNotEmpty() }
        // Words without a meaning (not in the 낚아보카 사전): translated as their part of speech.
        val filled = chosen.map { (d, words) ->
            d to words.map { w ->
                if (w.meaning.isNotBlank()) w
                else {
                    busy = "뜻 없는 단어를 번역하는 중… (${w.text})"
                    val m = runCatching { EnKoTranslator.ensureModel(); EnKoTranslator.translateAs(w.text, w.pos) }.getOrDefault("")
                    w.copy(meaning = m, pos = w.pos.ifEmpty { PasteImport.posOfMeaning(m) })
                }
            }
        }
        var first = -1L
        db.withTransaction {
            filled.forEach { (d, words) ->
                val id = d.existingId ?: dao.insertDeck(Deck(name = d.name.trim().ifEmpty { "붙여넣은 단어장" }))
                if (first < 0) first = id
                dao.insertWords(words.map { Word(deckId = id, text = it.text, meaning = it.meaning, pos = it.pos) })
            }
        }
        busy = null
        val count = filled.sumOf { it.second.size }
        Toast.makeText(context, "단어장 ${filled.size}개에 단어 ${count}개를 넣었어요", Toast.LENGTH_SHORT).show()
        if (first >= 0) onSaved(first, filled.size) else onDismiss()
    }

    Dialog(onDismissRequest = { if (busy == null) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp).imePadding(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (decks == null) "📋 붙여넣기로 단어 추가" else "확인하고 저장",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss, enabled = busy == null) { Text("닫기") }
                }
                busy?.let {
                    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text(it)
                    }
                    return@Column
                }
                val list = decks
                if (list == null) {
                    PasteInput(
                        text = text,
                        onText = { text = it },
                        intoOneDeck = targetDeckId != null,
                        onPaste = { clipboard.getText()?.text?.let { text = if (text.isBlank()) it else text.trimEnd() + "\n" + it } },
                        onNext = { read() },
                    )
                } else {
                    PastePreview(
                        decks = list,
                        intoOneDeck = targetDeckId != null,
                        onEdit = { editing = it },
                        onBack = { decks = null },
                        onSave = { save(list) },
                    )
                }
            }
        }
    }

    editing?.let { row ->
        val w = row.word
        CardEditDialog(
            title = "단어 고치기",
            initialText = w.text,
            initialMeaning = w.meaning,
            initialPos = w.pos,
            onDismiss = { editing = null },
            onConfirm = { t, m, p ->
                row.edited = PasteImport.PastedWord(t, m, p.ifEmpty { PasteImport.posOfMeaning(m) })
                row.include = true
                editing = null
            },
        )
    }
}

@Composable
private fun PasteInput(text: String, onText: (String) -> Unit, intoOneDeck: Boolean, onPaste: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            if (intoOneDeck) {
                "메모·인터넷·엑셀에서 복사한 단어 목록을 붙여넣으면 한 번에 이 단어장에 넣어요. 한 줄에 단어 하나씩 (영어 단어 + 뜻)."
            } else {
                "메모·인터넷·엑셀에서 복사한 단어 목록을 붙여넣으면 한 번에 넣어요. 단어장 여러 개를 넣으려면 단어장마다 맨 위에 '# 이름' 줄을 써요 (예: # 1과, [DAY 3], Unit 2, 3일차). 같은 이름의 단어장이 있으면 거기에 더해요."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            "뜻 앞에 품사를 써도 돼요: (동) 연결하다, v. 연결하다, 형용사: 빠른. 뜻을 비우면 사전 뜻이나 자동 번역을 넣어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPaste) { Text("📋 복사한 글 붙여넣기") }
            if (text.isNotEmpty()) TextButton(onClick = { onText("") }) { Text("지우기") }
            else TextButton(onClick = { onText(EXAMPLE) }) { Text("예시 보기") }
        }
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            placeholder = { Text(EXAMPLE, color = MaterialTheme.colorScheme.outline) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onNext, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("다음: 단어 확인하기")
        }
    }
}

@Composable
private fun PastePreview(
    decks: List<PasteDeck>,
    intoOneDeck: Boolean,
    onEdit: (PasteRow) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val rows = decks.flatMap { it.rows }
    val chosen = rows.count { it.include }
    val look = rows.count { it.include && it.edited == null && it.pick == "checked" && it.checked.needsLook }
    val deckCount = decks.count { d -> d.rows.any { it.include } }
    Column(Modifier.fillMaxSize()) {
        if (rows.isEmpty()) {
            Text(
                "영어 단어를 찾지 못했어요. 한 줄에 '영어 단어 뜻' 모양으로 붙여넣어 주세요.",
                modifier = Modifier.padding(vertical = 24.dp).weight(1f),
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("다시 붙여넣기") }
            return@Column
        }
        Text(
            "단어장 ${deckCount}개 · 단어 ${chosen}개" + if (look > 0) " · 확인할 단어 ${look}개" else "",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "모든 뜻을 낚아보카 사전과 한 번 더 맞춰 봤어요. 노란 줄은 한 번 봐 주세요. 단어를 누르면 고칠 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            decks.forEachIndexed { di, d ->
                item(key = "deck-$di") {
                    Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                        if (intoOneDeck) {
                            Text("📒 ${d.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        } else {
                            OutlinedTextField(
                                value = d.name,
                                onValueChange = { d.name = it },
                                label = { Text(if (d.existingId != null) "있는 단어장에 더해요" else "새 단어장 이름") },
                                singleLine = true,
                                enabled = d.existingId == null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val all = d.rows.all { it.include }
                        TextButton(onClick = { d.rows.forEach { r -> r.include = !all } }) {
                            Text(if (all) "이 단어장 모두 빼기" else "이 단어장 모두 넣기 (${d.rows.size}개)")
                        }
                    }
                }
                itemsIndexed(d.rows, key = { j, _ -> "word-$di-$j" }) { _, r -> PasteRowView(r, onEdit) }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("다시 붙여넣기") }
            Button(onClick = onSave, enabled = chosen > 0, modifier = Modifier.weight(1.4f).heightIn(min = 52.dp)) {
                Text("${chosen}개 저장")
            }
        }
    }
}

@Composable
private fun PasteRowView(r: PasteRow, onEdit: (PasteRow) -> Unit) {
    val c = r.checked
    val w = r.word
    val warn = r.include && r.edited == null && r.pick == "checked" && c.needsLook
    val bg = when {
        !r.include -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        warn -> Color(0x33FFC107)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).clickable { onEdit(r) }.padding(start = 2.dp, end = 10.dp, top = 4.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = r.include, onCheckedChange = { r.include = it })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    PosBadge(w.pos, Modifier.padding(start = 6.dp))
                }
                Text(
                    w.meaning.ifEmpty { "(저장할 때 자동 번역)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (w.meaning.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        val note = when {
            r.already -> "이 단어장에 이미 있어서 뺐어요"
            r.edited != null -> "직접 고쳤어요"
            r.pick == "original" -> "붙여넣은 그대로예요"
            r.pick == "offer" -> "사전 뜻으로 바꿨어요"
            c.note == Note.SPELLING_FIXED -> "스펠링을 고쳤어요 (${c.original.text} → ${c.word.text})"
            c.note == Note.DIFFERENT -> "사전 뜻과 달라요 · 사전: ${c.offer?.meaning}"
            else -> c.note.label
        }
        if (note.isNotEmpty()) {
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = if (warn) Color(0xFFB26A00) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
        if (r.edited == null && !r.already) {
            val fixed = c.note == Note.POS_FIXED || c.note == Note.SPELLING_FIXED || c.note == Note.FROM_DICTIONARY
            Row(Modifier.padding(start = 40.dp)) {
                if (c.offer != null) {
                    TextButton(onClick = { r.pick = if (r.pick == "offer") "checked" else "offer"; r.include = true }) {
                        Text(if (r.pick == "offer") "내 뜻으로 되돌리기" else "사전 뜻으로 바꾸기")
                    }
                }
                if (fixed && c.original != c.word) {
                    TextButton(onClick = { r.pick = if (r.pick == "original") "checked" else "original" }) {
                        Text(if (r.pick == "original") "고친 걸로" else "원래대로")
                    }
                }
            }
        }
    }
}
