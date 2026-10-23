package com.wordbook.app.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.BlankLevel
import com.wordbook.app.data.DailyStats
import com.wordbook.app.net.Telegram
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.PointerEventPass
import com.wordbook.app.data.Srs
import com.wordbook.app.data.StudyMode
import com.wordbook.app.data.StudySettings
import com.wordbook.app.data.Word
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val KnownGreen = Color(0xFF2E7D32)
private val UnknownRed = Color(0xFFC62828)
private val MasteredBlue = Color(0xFF1565C0)

enum class Answer { KNOWN, MASTERED, UNKNOWN }

/**
 * A study session works like Flashcards Deluxe's "hand": up to [StudySettings.handSize] cards go
 * round and round; a card leaves after [StudySettings.knownToLeave] × 헷갈려요 or one 외웠어요,
 * and the next waiting card takes its place. The review schedule is updated once per card,
 * on its first answer in the session.
 */
class StudyViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val settings = StudySettings(app)
    /** -1 with [review] = today's reviews across all decks; -1 with [allDecks] = every deck together. */
    private val deckId: Long = checkNotNull(handle.get<Long>("deckId"))
    private val onlyUnknown: Boolean = handle.get<Boolean>("onlyUnknown") ?: false
    val review: Boolean = handle.get<Boolean>("review") ?: false
    private val allDecks: Boolean = handle.get<Boolean>("all") ?: false
    /** Study mode picked for this session on the deck screen; otherwise the settings mode. */
    private val modeChoice: StudyMode =
        handle.get<String>("mode")?.let { runCatching { StudyMode.valueOf(it) }.getOrNull() } ?: settings.mode

    var loading by mutableStateOf(true)
        private set
    // Card order options live in the one settings screen (they used to be per deck).
    val backFirst = settings.backFirst
    private val shuffle = settings.shuffle
    private val repeatMissed = settings.repeatMissed
    private val handSize = settings.handSize.coerceAtLeast(1)
    private val knownToLeave = settings.knownToLeave.coerceAtLeast(1)
    val autoSpeak: Boolean get() = settings.autoSpeak

    // Study timer: counts only while the screen is being touched; pauses after idleSeconds.
    private val stats = DailyStats(app)
    private val idleMs = settings.idleSeconds * 1000L
    val goalMs = settings.dailyGoalMinutes * 60_000L
    private var lastTouch = System.currentTimeMillis()
    var todayMs by mutableStateOf(stats.studyMs)
        private set
    var paused by mutableStateOf(false)
        private set
    var goalReached by mutableStateOf(goalMs > 0 && stats.studyMs >= goalMs)
        private set
    var telegramStatus by mutableStateOf<String?>(null)
        private set
    val telegramReady: Boolean get() = settings.telegramToken.isNotBlank() && settings.telegramChatId.isNotBlank()

    private val hand = mutableListOf<Word>()
    private val waiting = ArrayDeque<Word>()
    private val knownTimes = mutableMapOf<Long, Int>()
    /** Cards whose review schedule was already updated in this session. */
    private val scheduled = mutableSetOf<Long>()
    /** Cards that were already in review (box > 0) when the session started. */
    private var reviewedBefore = setOf<Long>()
    private val undoStack = ArrayDeque<Snapshot>()

    var current by mutableStateOf<Word?>(null)
        private set
    /** Counts questions shown, so each one gets fresh UI state. */
    var turn by mutableStateOf(0)
        private set
    var doneCount by mutableStateOf(0)
        private set
    var sessionSize by mutableStateOf(0)
        private set
    var flipped by mutableStateOf(false)
    var finished by mutableStateOf(false)
        private set
    var canUndo by mutableStateOf(false)
        private set

    // Current question (for quiz modes).
    var mode by mutableStateOf(StudyMode.CARD)
        private set
    var choices by mutableStateOf(listOf<Word>())
        private set
    var pattern by mutableStateOf("")
        private set
    /** null = not answered yet, true/false = right/wrong. */
    var answered by mutableStateOf<Boolean?>(null)
        private set
    var pickedId by mutableStateOf<Long?>(null)
        private set

    /** All words, for wrong options in multiple choice. */
    private var pool = listOf<Word>()
    private var sessionWords = listOf<Word>()
    /** Words missed at least once in this session (for the summary report). */
    val everMissed = mutableStateListOf<Word>()
    var firstTryKnown by mutableStateOf(0)
        private set
    var masteredCount by mutableStateOf(0)
        private set

    val progress: Float get() = if (sessionSize == 0) 0f else doneCount / sessionSize.toFloat()

    private class Snapshot(
        val hand: List<Word>,
        val waiting: List<Word>,
        val knownTimes: Map<Long, Int>,
        val scheduled: Set<Long>,
        val everMissed: List<Word>,
        val doneCount: Int,
        val firstTryKnown: Int,
        val masteredCount: Int,
        /** The word as it was in the database before this answer changed it. */
        val wordBefore: Word,
    )

    init {
        viewModelScope.launch {
            val all = dao.getAllWords()
            pool = all
            sessionWords = when {
                review -> dao.getDueWords(System.currentTimeMillis())
                allDecks -> all.filter { !it.excluded }.let { w -> w.filter { !it.mastered }.ifEmpty { w } }
                else -> {
                    val words = dao.getWords(deckId).filter { !it.excluded }
                    if (words.size >= 4) pool = words
                    // Mastered cards are skipped, unless that leaves nothing to study.
                    if (onlyUnknown) words.filter { !it.known }
                    else words.filter { !it.mastered }.ifEmpty { words }
                }
            }
            val limit = settings.sessionSize
            sessionWords = order(sessionWords).let { if (limit > 0) it.take(limit) else it }
            start(sessionWords)
            loading = false
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (loading || finished) continue
                paused = System.currentTimeMillis() - lastTouch > idleMs
                if (!paused) {
                    stats.addStudyMs(1000)
                    todayMs = stats.studyMs
                    checkGoal()
                }
            }
        }
    }

    fun touch() {
        lastTouch = System.currentTimeMillis()
        paused = false
    }

    private fun checkGoal() {
        if (goalMs <= 0 || todayMs < goalMs || stats.goalNotified) return
        stats.goalNotified = true
        goalReached = true
        if (telegramReady) sendTelegram()
    }

    fun sendTelegram() {
        viewModelScope.launch {
            telegramStatus = "텔레그램으로 보내는 중…"
            val error = Telegram.send(settings.telegramToken, settings.telegramChatId, stats.summary(settings.dailyGoalMinutes))
            telegramStatus = if (error == null) "텔레그램으로 학습 결과를 보냈어요" else "텔레그램 전송 실패: $error"
        }
    }

    private fun order(words: List<Word>) = if (shuffle) words.shuffled() else words.sortedBy { it.text.lowercase() }

    private fun start(words: List<Word>) {
        hand.clear()
        waiting.clear()
        waiting.addAll(order(words))
        knownTimes.clear()
        scheduled.clear()
        undoStack.clear()
        canUndo = false
        reviewedBefore = words.filter { it.box > 0 }.map { it.id }.toSet()
        sessionSize = words.size
        doneCount = 0
        finished = words.isEmpty()
        refill()
        prepare()
    }

    private fun refill() {
        while (hand.size < handSize && waiting.isNotEmpty()) hand += waiting.removeFirst()
    }

    /** Sets up the question for the card at the front of the hand. */
    private fun prepare() {
        flipped = false
        answered = null
        pickedId = null
        turn++
        val word = hand.firstOrNull()
        current = word
        if (word == null) {
            finished = true
            return
        }
        var m = when (modeChoice) {
            // Gets harder as the word climbs the review boxes.
            StudyMode.STAGED -> when (word.box) {
                0 -> StudyMode.CARD
                1 -> StudyMode.CHOICE
                2 -> StudyMode.SPELLING
                else -> StudyMode.DICTATION
            }
            StudyMode.MIXED -> listOf(StudyMode.CARD, StudyMode.CHOICE, StudyMode.SPELLING, StudyMode.DICTATION).random()
            else -> modeChoice
        }
        val others = pool.filter { it.id != word.id && !it.text.equals(word.text, true) }.distinctBy { it.text.lowercase() }
        if (m == StudyMode.CHOICE && others.size < 3) m = StudyMode.CARD
        if (m != StudyMode.CARD && word.text.length < 2) m = StudyMode.CARD
        mode = m
        if (m == StudyMode.CHOICE) {
            // Prefer wrong options with the same part of speech so the answer isn't obvious.
            val same = others.filter { it.pos == word.pos && word.pos.isNotEmpty() }.shuffled()
            val wrong = (same + others.shuffled()).distinctBy { it.id }.take(3)
            choices = (wrong + word).shuffled()
        }
        if (m == StudyMode.SPELLING) pattern = makePattern(word)
    }

    private fun makePattern(word: Word): String {
        val level = when (settings.blankLevel) {
            BlankLevel.AUTO -> if (word.box <= 2) BlankLevel.EASY else if (word.box == 3) BlankLevel.NORMAL else BlankLevel.HARD
            else -> settings.blankLevel
        }
        val text = word.text
        val letters = text.indices.filter { text[it].isLetter() }
        if (level == BlankLevel.HARD) return text.map { if (it.isLetter()) '_' else it }.joinToString("")
        val ratio = if (level == BlankLevel.EASY) 0.3 else 0.5
        // One continuous run of blanks, typed in order. Keep the first letter as a hint on longer words.
        val candidates = if (letters.size > 3) letters.drop(1) else letters
        val count = (letters.size * ratio).roundToInt().coerceIn(1, candidates.size)
        val startAt = (0..candidates.size - count).random()
        val blanks = candidates.subList(startAt, startAt + count).toSet()
        return text.mapIndexed { i, c -> if (i in blanks) '_' else c }.joinToString("")
    }

    fun restart(onlyMissed: Boolean) {
        viewModelScope.launch {
            // Reload so the new session sees the updated review boxes.
            val ids = (if (onlyMissed) everMissed.toList() else sessionWords).map { it.id }
            val words = ids.mapNotNull { dao.getWord(it) }.filter { !it.excluded }
            sessionWords = words
            everMissed.clear()
            firstTryKnown = 0
            masteredCount = 0
            start(words)
        }
    }

    fun pick(choice: Word) {
        touch()
        val word = current ?: return
        pickedId = choice.id
        answered = choice.id == word.id
    }

    fun submit(input: String) {
        touch()
        val word = current ?: return
        answered = input.trim().equals(word.text.trim(), ignoreCase = true)
    }

    /** After a quiz answer has been shown. */
    fun next() = mark(if (answered == true) Answer.KNOWN else Answer.UNKNOWN)

    fun mark(answer: Answer) {
        touch()
        val word = hand.firstOrNull() ?: return
        undoStack.addLast(
            Snapshot(
                hand.toList(), waiting.toList(), knownTimes.toMap(), scheduled.toSet(), everMissed.toList(),
                doneCount, firstTryKnown, masteredCount, word,
            ),
        )
        if (undoStack.size > 30) undoStack.removeFirst()
        canUndo = true
        stats.recordAnswer(word.text, answer != Answer.UNKNOWN)

        val firstAnswer = word.id !in scheduled
        val updated = when {
            firstAnswer -> Srs.apply(
                word,
                when (answer) {
                    Answer.KNOWN -> Srs.Result.RIGHT
                    Answer.MASTERED -> Srs.Result.EASY
                    Answer.UNKNOWN -> Srs.Result.WRONG
                },
            )
            answer == Answer.MASTERED -> word.copy(mastered = true, known = true)
            else -> word
        }
        scheduled += word.id
        if (updated != word) viewModelScope.launch { dao.updateWord(updated) }

        if (firstAnswer && answer != Answer.UNKNOWN) firstTryKnown++
        if (answer == Answer.MASTERED) masteredCount++
        if (answer == Answer.UNKNOWN && everMissed.none { it.id == word.id }) everMissed += word
        val times = (knownTimes[word.id] ?: 0) + if (answer == Answer.KNOWN) 1 else 0
        knownTimes[word.id] = times

        val leaves = !repeatMissed ||
            answer == Answer.MASTERED ||
            times >= knownToLeave ||
            // A review word recalled right away doesn't need the extra rounds.
            (firstAnswer && answer == Answer.KNOWN && word.id in reviewedBefore)
        hand.removeAt(0)
        if (leaves) doneCount++ else hand += updated
        refill()
        prepare()
    }

    /** Swipe right: go back to the previous card and take back its answer. */
    fun undo() {
        touch()
        val s = undoStack.removeLastOrNull() ?: return
        hand.clear(); hand.addAll(s.hand)
        waiting.clear(); waiting.addAll(s.waiting)
        knownTimes.clear(); knownTimes.putAll(s.knownTimes)
        scheduled.clear(); scheduled.addAll(s.scheduled)
        everMissed.clear(); everMissed.addAll(s.everMissed)
        doneCount = s.doneCount
        firstTryKnown = s.firstTryKnown
        masteredCount = s.masteredCount
        viewModelScope.launch { dao.updateWord(s.wordBefore) }
        canUndo = undoStack.isNotEmpty()
        finished = false
        prepare()
    }

    /** Applies a change to the current card (edit, flag) in the session and the database. */
    private fun changeCurrent(transform: (Word) -> Word) {
        val word = hand.firstOrNull() ?: return
        val changed = transform(word)
        hand[0] = changed
        current = changed
        viewModelScope.launch {
            dao.getWord(word.id)?.let { dao.updateWord(transform(it)) }
        }
    }

    fun editCurrent(text: String, meaning: String, pos: String) =
        changeCurrent { it.copy(text = text, meaning = meaning.ifBlank { it.meaning }, pos = pos) }

    fun toggleFlag() = changeCurrent { it.copy(flagged = !it.flagged) }

    /** Takes the current card out of this and future sessions. */
    fun excludeCurrent() {
        val word = hand.firstOrNull() ?: return
        viewModelScope.launch { dao.getWord(word.id)?.let { dao.updateWord(it.copy(excluded = true)) } }
        hand.removeAt(0)
        sessionSize--
        undoStack.clear()
        canUndo = false
        refill()
        prepare()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { StudyViewModel(this[APPLICATION_KEY]!!, createSavedStateHandle()) }
        }
    }
}

@Composable
private fun rememberTts(): TextToSpeech? {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }
    if (ready) tts.language = Locale.US
    return if (ready) tts else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onBack: () -> Unit,
    vm: StudyViewModel = viewModel(factory = StudyViewModel.Factory),
) {
    val tts = rememberTts()
    var confirmExit by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val inSession = !vm.loading && !vm.finished
    // Leaving in the middle of a session asks first, from the back button and the system back gesture.
    BackHandler(enabled = inSession) { confirmExit = true }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Text(
                        if (vm.finished) "학습 결과"
                        else (if (vm.review) "복습 " else "") +
                            "${vm.doneCount}/${vm.sessionSize} · ${(vm.progress * 100).roundToInt()}%",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (inSession) confirmExit = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                },
                actions = {
                    StudyTimer(vm)
                    if (inSession && vm.current != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "카드 메뉴") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("카드 수정") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menuOpen = false; editing = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (vm.current?.flagged == true) "수정 필요 표시 지우기" else "오타·수정 필요 표시") },
                                    leadingIcon = { Icon(Icons.Default.Flag, null) },
                                    onClick = { menuOpen = false; vm.toggleFlag() },
                                )
                                DropdownMenuItem(
                                    text = { Text("이 카드 학습에서 빼기") },
                                    leadingIcon = { Icon(Icons.Default.Block, null) },
                                    onClick = { menuOpen = false; vm.excludeCurrent() },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).pointerInput(Unit) {
                // Any touch counts as studying; nothing is consumed so gestures still work.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        vm.touch()
                    }
                }
            },
        ) {
            when {
                vm.loading -> {}
                vm.finished -> Summary(vm, onBack)
                else -> vm.current?.let { word ->
                    // imePadding keeps the content above the keyboard in typing quizzes.
                    val typing = vm.mode == StudyMode.SPELLING || vm.mode == StudyMode.DICTATION
                    Column(
                        Modifier.fillMaxSize().imePadding()
                            .padding(horizontal = 24.dp, vertical = if (typing) 4.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ThickProgress(vm.progress, Modifier.fillMaxWidth())
                        val notes = listOfNotNull(
                            if (word.flagged) "⚑ 수정 필요로 표시한 카드" else null,
                            if (vm.goalReached) "🎉 오늘 목표 시간 달성!" else null,
                            vm.telegramStatus,
                        )
                        if (notes.isNotEmpty()) {
                            Text(
                                notes.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(if (typing) 8.dp else 16.dp))
                        val speak = { tts?.speak(word.text, TextToSpeech.QUEUE_FLUSH, null, "w${word.id}") }
                        // Speak automatically for dictation, and for every card when the setting is on.
                        LaunchedEffect(vm.turn, tts != null) {
                            if (tts != null && (vm.mode == StudyMode.DICTATION || vm.autoSpeak)) speak()
                        }
                        // key() resets swipe offset, flip animation and typed text for each new question.
                        key(vm.turn) {
                            when (vm.mode) {
                                StudyMode.CHOICE -> ChoiceQuiz(
                                    word = word,
                                    choices = vm.choices,
                                    reverse = vm.backFirst,
                                    answered = vm.answered,
                                    pickedId = vm.pickedId,
                                    onPick = vm::pick,
                                    onNext = vm::next,
                                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                )
                                StudyMode.SPELLING, StudyMode.DICTATION -> TypingQuiz(
                                    word = word,
                                    pattern = if (vm.mode == StudyMode.SPELLING) vm.pattern
                                    else word.text.map { if (it.isLetter()) '_' else it }.joinToString(""),
                                    dictation = vm.mode == StudyMode.DICTATION,
                                    answered = vm.answered,
                                    onSubmit = vm::submit,
                                    onActivity = vm::touch,
                                    onNext = vm::next,
                                    onSpeak = { speak() },
                                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                )
                                else -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    SwipeCard(
                                        word = word,
                                        backFirst = vm.backFirst,
                                        flipped = vm.flipped,
                                        canUndo = vm.canUndo,
                                        onFlip = { vm.flipped = !vm.flipped },
                                        onSpeak = { speak() },
                                        onResult = vm::mark,
                                        onUndo = vm::undo,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // One guide line instead of buttons: the gestures themselves are the controls.
                                    Text(
                                        "카드를 탭하면 해석이 보여요" + if (vm.canUndo) " · → 이전 카드" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text("↓ 몰라요", color = UnknownRed, fontWeight = FontWeight.Bold)
                                        Text("← 헷갈려요", color = KnownGreen, fontWeight = FontWeight.Bold)
                                        Text("↑ 외웠어요", color = MasteredBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("학습을 끝낼까요?") },
            text = { Text("지금까지 푼 카드는 저장돼요.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("끝내기") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("계속하기") } },
        )
    }
    if (editing) {
        vm.current?.let { w ->
            CardEditDialog(
                title = "카드 수정",
                initialText = w.text,
                initialMeaning = w.meaning,
                initialPos = w.pos,
                onDismiss = { editing = false },
                onConfirm = { t, m, p -> vm.editCurrent(t, m, p); editing = false },
            )
        }
    }
}

@Composable
private fun SwipeCard(
    word: Word,
    backFirst: Boolean,
    flipped: Boolean,
    canUndo: Boolean,
    onFlip: () -> Unit,
    onSpeak: () -> Unit,
    onResult: (Answer) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // A short drag or a quick flick is enough to answer.
    val threshold = with(density) { 48.dp.toPx() }
    val flingVelocity = with(density) { 600.dp.toPx() }
    val velocity = remember { VelocityTracker() }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(350), label = "flip")

    // Swipe feedback: green check (left, 헷갈려요), blue double check (up, 외웠어요), red X (down, 몰라요).
    val leftProgress = (-offsetX.value / threshold).coerceIn(0f, 1f)
    val upProgress = (-offsetY.value / threshold).coerceIn(0f, 1f)
    val downProgress = (offsetY.value / threshold).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer { rotationZ = offsetX.value / 40f }
            .pointerInput(word.id, canUndo) {
                detectDragGestures(
                    onDragStart = { velocity.resetTracking() },
                    onDrag = { change, drag ->
                        velocity.addPointerInputChange(change)
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + drag.x)
                            offsetY.snapTo(offsetY.value + drag.y)
                        }
                    },
                    onDragEnd = {
                        val v = velocity.calculateVelocity()
                        scope.launch {
                            val x = offsetX.value
                            val y = offsetY.value
                            val horizontal = abs(x) >= abs(y)
                            when {
                                horizontal && (x < -threshold || v.x < -flingVelocity) -> {
                                    offsetX.animateTo(-size.width * 1.5f, tween(200)); onResult(Answer.KNOWN)
                                }
                                // Swipe right brings back the previous card.
                                horizontal && canUndo && (x > threshold || v.x > flingVelocity) -> {
                                    offsetX.animateTo(size.width * 1.5f, tween(150)); onUndo()
                                }
                                !horizontal && (y < -threshold || v.y < -flingVelocity) -> {
                                    offsetY.animateTo(-size.height * 1.5f, tween(200)); onResult(Answer.MASTERED)
                                }
                                !horizontal && (y > threshold || v.y > flingVelocity) -> {
                                    offsetY.animateTo(size.height * 1.5f, tween(200)); onResult(Answer.UNKNOWN)
                                }
                                else -> {
                                    launch { offsetX.animateTo(0f) }
                                    offsetY.animateTo(0f)
                                }
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            onClick = onFlip,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f, matchHeightConstraintsFirst = true)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density.density
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        ) {
            val showingBack = rotation > 90f
            // The side shown first is the "front"; with backFirst the meaning comes first.
            val showWord = showingBack == backFirst
            Box(
                Modifier.fillMaxSize().graphicsLayer { if (showingBack) rotationY = 180f }.padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (showWord) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(word.text, fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        IconButton(onClick = onSpeak) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "발음 듣기") }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (word.pos.isNotEmpty()) {
                            PosBadge(word.pos, fontSize = 18.sp)
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            word.meaning.ifBlank { "(뜻 없음)" },
                            fontSize = 30.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        if (leftProgress > 0f) SwipeBadge(Icons.Default.Check, KnownGreen, leftProgress)
        if (upProgress > 0f) SwipeBadge(Icons.Default.DoneAll, MasteredBlue, upProgress)
        if (downProgress > 0f) SwipeBadge(Icons.Default.Close, UnknownRed, downProgress)
    }
}

@Composable
private fun SwipeBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, alpha: Float) {
    Box(
        Modifier.size(96.dp).graphicsLayer { this.alpha = alpha }
            .background(color, RoundedCornerShape(48.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
}

private fun formatTime(ms: Long): String {
    val sec = ms / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}

/** "⏱ 3:12 / 10:00" in the top bar; shows a pause sign while the timer is stopped. */
@Composable
private fun StudyTimer(vm: StudyViewModel) {
    val goal = if (vm.goalMs > 0) " / ${formatTime(vm.goalMs)}" else ""
    Text(
        (if (vm.paused) "⏸ " else "⏱ ") + formatTime(vm.todayMs) + goal,
        style = MaterialTheme.typography.titleSmall,
        color = if (vm.paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 16.dp),
    )
}

@Composable
private fun Summary(vm: StudyViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("수고했어요!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "오늘 학습 시간 ${formatTime(vm.todayMs)}" +
                if (vm.goalMs > 0) (if (vm.todayMs >= vm.goalMs) " · 목표 달성 🎉" else " · 목표까지 ${formatTime(vm.goalMs - vm.todayMs)}") else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${vm.sessionSize}개 중 처음에 바로 맞힌 카드 ${vm.firstTryKnown}개 (외웠어요 ${vm.masteredCount}개) · 틀린 카드 ${vm.everMissed.size}개",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        if (vm.everMissed.isNotEmpty()) {
            Text("틀렸던 단어", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f)) {
                items(vm.everMissed, key = { it.id }) { w ->
                    ListItem(
                        headlineContent = {
                            Text(w.text)
                        },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PosBadge(w.pos, Modifier.padding(end = 6.dp))
                                Text(w.meaning)
                            }
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.everMissed.isNotEmpty()) PrimaryButton("틀린 단어만 다시 학습", { vm.restart(onlyMissed = true) })
            SecondaryButton("처음부터 다시", { vm.restart(onlyMissed = false) })
            if (vm.telegramReady) {
                SecondaryButton("텔레그램으로 결과 보내기", vm::sendTelegram)
                vm.telegramStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            SecondaryButton("끝내기", onBack)
        }
    }
}
