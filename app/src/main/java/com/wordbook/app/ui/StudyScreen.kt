package com.wordbook.app.ui

import android.app.Application
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import com.wordbook.app.data.Word
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val KnownGreen = Color(0xFF2E7D32)
private val UnknownRed = Color(0xFFC62828)
private val MasteredBlue = Color(0xFF1565C0)

enum class Answer { KNOWN, MASTERED, UNKNOWN }

class StudyViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val deckId: Long = checkNotNull(handle.get<Long>("deckId"))
    private val onlyUnknown: Boolean = handle.get<Boolean>("onlyUnknown") ?: false

    var loading by mutableStateOf(true)
        private set
    var backFirst by mutableStateOf(false)
    private var shuffle = true
    private var repeatMissed = true

    private var queue = listOf<Word>()
    var index by mutableStateOf(0)
        private set
    var round by mutableStateOf(1)
        private set
    var flipped by mutableStateOf(false)
    var finished by mutableStateOf(false)
        private set

    private var sessionWords = listOf<Word>()
    /** Words missed at least once in this session (for the summary report). */
    val everMissed = mutableStateListOf<Word>()
    private val missedThisRound = mutableListOf<Word>()
    var firstTryKnown by mutableStateOf(0)
        private set
    var masteredCount by mutableStateOf(0)
        private set

    val current: Word? get() = queue.getOrNull(index)
    val roundSize: Int get() = queue.size
    val sessionSize: Int get() = sessionWords.size

    init {
        viewModelScope.launch {
            val deck = dao.getDeck(deckId)
            shuffle = deck?.shuffle ?: true
            repeatMissed = deck?.repeatMissed ?: true
            backFirst = deck?.backFirst ?: false
            val all = dao.getWords(deckId)
            // Mastered cards are skipped, unless that leaves nothing to study.
            sessionWords = if (onlyUnknown) all.filter { !it.known }
            else all.filter { !it.mastered }.ifEmpty { all }
            start(sessionWords)
            loading = false
        }
    }

    private fun order(words: List<Word>) = if (shuffle) words.shuffled() else words.sortedBy { it.text.lowercase() }

    private fun start(words: List<Word>) {
        queue = order(words)
        index = 0
        flipped = false
        finished = queue.isEmpty()
        missedThisRound.clear()
    }

    fun restart(onlyMissed: Boolean) {
        val words = if (onlyMissed) everMissed.toList() else sessionWords
        sessionWords = words
        everMissed.clear()
        firstTryKnown = 0
        masteredCount = 0
        round = 1
        start(words)
    }

    fun mark(answer: Answer) {
        val word = current ?: return
        viewModelScope.launch {
            when (answer) {
                Answer.KNOWN -> dao.markKnown(word.id)
                Answer.MASTERED -> dao.markMastered(word.id)
                Answer.UNKNOWN -> dao.markUnknown(word.id)
            }
        }
        if (answer != Answer.UNKNOWN) {
            if (round == 1) firstTryKnown++
            if (answer == Answer.MASTERED) masteredCount++
        } else {
            missedThisRound += word
            if (everMissed.none { it.id == word.id }) everMissed += word
        }
        if (index + 1 < queue.size) {
            index++
            flipped = false
        } else if (repeatMissed && missedThisRound.isNotEmpty()) {
            round++
            start(missedThisRound.toList())
        } else {
            finished = true
        }
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (vm.finished) "학습 결과"
                        else if (vm.round > 1) "다시 보기 ${vm.round - 1}회차 · ${vm.index + 1}/${vm.roundSize}"
                        else "${vm.index + 1} / ${vm.roundSize}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                vm.loading -> {}
                vm.finished -> Summary(vm, onBack)
                else -> vm.current?.let { word ->
                    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val progress = vm.index / vm.roundSize.toFloat()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        // key() resets swipe offset & flip animation for each new card.
                        key(word.id, vm.round) {
                            SwipeCard(
                                word = word,
                                backFirst = vm.backFirst,
                                flipped = vm.flipped,
                                onFlip = { vm.flipped = !vm.flipped },
                                onSpeak = { tts?.speak(word.text, TextToSpeech.QUEUE_FLUSH, null, "w${word.id}") },
                                onResult = vm::mark,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        // One guide line instead of buttons: the gestures themselves are the controls.
                        Text(
                            "카드를 탭하면 해석이 보여요",
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

@Composable
private fun SwipeCard(
    word: Word,
    backFirst: Boolean,
    flipped: Boolean,
    onFlip: () -> Unit,
    onSpeak: () -> Unit,
    onResult: (Answer) -> Unit,
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
            .pointerInput(word.id) {
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
            elevation = CardDefaults.cardElevation(6.dp),
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
                        PosOptions.firstOrNull { it.first == word.pos }?.let { (code, label) ->
                            PosBadge(code, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
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

@Composable
private fun Summary(vm: StudyViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("수고했어요!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "${vm.sessionSize}개 중 처음에 맞힌 카드 ${vm.firstTryKnown}개 (외웠어요 ${vm.masteredCount}개) · 틀린 카드 ${vm.everMissed.size}개",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        if (vm.everMissed.isNotEmpty()) {
            Text("틀렸던 단어", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f)) {
                items(vm.everMissed, key = { it.id }) { w ->
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(w.text)
                                PosBadge(w.pos, Modifier.padding(start = 6.dp))
                            }
                        },
                        supportingContent = { Text(w.meaning) },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (vm.everMissed.isNotEmpty()) {
            Button(onClick = { vm.restart(onlyMissed = true) }, modifier = Modifier.fillMaxWidth()) {
                Text("틀린 단어만 다시 학습")
            }
        }
        OutlinedButton(onClick = { vm.restart(onlyMissed = false) }, modifier = Modifier.fillMaxWidth()) {
            Text("처음부터 다시")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("끝내기") }
    }
}
