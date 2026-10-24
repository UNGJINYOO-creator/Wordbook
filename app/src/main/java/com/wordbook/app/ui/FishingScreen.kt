package com.wordbook.app.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordbook.app.data.AnswerEntry
import com.wordbook.app.data.AnswerLog
import com.wordbook.app.data.AppDatabase
import com.wordbook.app.data.DailyStats
import com.wordbook.app.data.Fish
import com.wordbook.app.data.Fishing
import com.wordbook.app.data.Game
import com.wordbook.app.data.StudyMode
import com.wordbook.app.data.StudySettings
import com.wordbook.app.data.Word
import com.wordbook.app.data.WrongNote
import com.wordbook.app.net.Telegram
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class FishPhase { INTRO, PLAYING, WON, LOST }

/**
 * 단어 낚시 도전: a time attack on today's words. The line starts with some seconds and runs down;
 * right answers reel in seconds, wrong ones lose some. Reaching the goal lands the fish and ends the day.
 */
class FishingViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).wordDao()
    private val settings = StudySettings(app)
    private val stats = DailyStats(app)
    private val wrongNote = WrongNote(app)
    private val answerLog = AnswerLog(app)
    private val game = Game(app)
    val fishing = Fishing(app)
    private val audio = app.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    private val sfx = com.wordbook.app.data.Sfx(app)
    private val tone = runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60) }.getOrNull()

    private var pool = listOf<Word>()
    private val recent = ArrayDeque<Long>()

    var phase by mutableStateOf(FishPhase.INTRO)
        private set
    /** Why the game is closed right now, or null when it can be played. */
    var lockReason by mutableStateOf<String?>(null)
        private set
    var triesLeft by mutableIntStateOf(fishing.triesLeft)
        private set
    var fish by mutableStateOf(Fishing.species.first())
        private set
    var seconds by mutableDoubleStateOf(fishing.start)
        private set
    val goal: Double get() = fishing.goal

    var word by mutableStateOf<Word?>(null)
        private set
    var mode by mutableStateOf(StudyMode.CHOICE)
        private set
    var choices by mutableStateOf(listOf<Word>())
        private set
    var pattern by mutableStateOf("")
        private set
    var answered by mutableStateOf<Boolean?>(null)
        private set
    var pickedId by mutableStateOf<Long?>(null)
        private set
    private var turn = 0
    private var questionAt = 0L

    var combo by mutableIntStateOf(0)
        private set
    var right by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    val missed = mutableListOf<Word>()
    private var startedAt = 0L
    var durationMs by mutableStateOf(0L)
        private set
    var kg by mutableDoubleStateOf(0.0)
        private set
    var newRecord by mutableStateOf(false)
        private set
    /** The "+5초" / "-3초" pop over the sea, and a counter so the same text pops again. */
    var pop by mutableStateOf<String?>(null)
        private set
    var popId by mutableIntStateOf(0)
        private set
    /** Bumped on a wrong answer so the fish jerks the line. */
    var pullId by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch {
            val all = dao.getAllWords().filter { !it.excluded && it.meaning.isNotBlank() && it.text.length >= 2 }
            val todayIds = stats.learnedToday + wrongNote.ids
            val today = all.filter { it.id in todayIds }
            val studied = all.filter { it.box > 0 && it.id !in todayIds }.shuffled()
            pool = (today + studied).distinctBy { it.text.lowercase() }
            if (pool.size < 8) pool = (pool + all.shuffled()).distinctBy { it.text.lowercase() }.take(maxOf(pool.size, 30))
            refreshLock()
        }
        viewModelScope.launch {
            while (true) {
                delay(100)
                if (phase == FishPhase.PLAYING) {
                    seconds -= fishing.drainPerSecond(seconds) * 0.1
                    if (seconds <= 0) lose()
                }
            }
        }
    }

    fun refreshLock() {
        triesLeft = fishing.triesLeft
        val goalMinutes = settings.dailyGoalMinutes
        val goalWords = settings.dailyWords
        lockReason = when {
            pool.size < 4 -> "단어가 4개 이상 있어야 낚시를 할 수 있어요"
            goalMinutes > 0 && stats.studyMs < goalMinutes * 60_000L ->
                "오늘 학습 시간 ${goalMinutes}분을 채우면 열려요 (지금 ${stats.studyMs / 60_000}분)"
            goalMinutes <= 0 && goalWords > 0 && stats.learnedCount < goalWords ->
                "오늘 목표 ${goalWords}단어를 채우면 열려요 (지금 ${stats.learnedCount}개)"
            triesLeft <= 0 -> "오늘 도전 기회를 다 썼어요. 내일 다시 도전해요!"
            else -> null
        }
    }

    fun start() {
        refreshLock()
        if (lockReason != null) return
        fishing.useTry()
        triesLeft = fishing.triesLeft
        fish = Fishing.fishFor(game.level)
        seconds = fishing.start
        combo = 0; right = 0; total = 0
        missed.clear()
        recent.clear()
        startedAt = System.currentTimeMillis()
        phase = FishPhase.PLAYING
        nextQuestion()
    }

    private fun nextQuestion() {
        if (phase != FishPhase.PLAYING) return
        turn++
        answered = null
        pickedId = null
        questionAt = System.currentTimeMillis()
        val w = pool.filter { it.id !in recent }.randomOrNull() ?: pool.random()
        recent.addLast(w.id)
        if (recent.size > minOf(6, pool.size - 1)) recent.removeFirst()
        word = w
        val roll = Math.random()
        val muted = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0
        mode = when {
            roll < 0.6 -> StudyMode.CHOICE
            roll < 0.85 || muted -> StudyMode.SPELLING
            else -> StudyMode.DICTATION
        }
        if (mode == StudyMode.CHOICE) {
            val others = pool.filter { it.id != w.id && !it.text.equals(w.text, true) }
            val same = others.filter { it.pos == w.pos && w.pos.isNotEmpty() }.shuffled()
            choices = ((same + others.shuffled()).distinctBy { it.id }.take(3) + w).shuffled()
        } else {
            pattern = blanks(w.text)
        }
    }

    /** About half the letters blank in one run, keeping the first letter as a hint on longer words. */
    private fun blanks(text: String): String {
        val letters = text.indices.filter { text[it].isLetter() }
        if (mode == StudyMode.DICTATION) return text.map { if (it.isLetter()) '_' else it }.joinToString("")
        val candidates = if (letters.size > 3) letters.drop(1) else letters
        val count = (letters.size * 0.5).roundToInt().coerceIn(1, candidates.size)
        val startAt = (0..candidates.size - count).random()
        val set = candidates.subList(startAt, startAt + count).toSet()
        return text.mapIndexed { i, c -> if (i in set) '_' else c }.joinToString("")
    }

    fun pick(choice: Word) {
        if (answered != null) return
        pickedId = choice.id
        answer(choice.id == word?.id, choice.text)
    }

    fun submit(input: String) {
        if (answered != null) return
        answer(input.trim().equals(word?.text?.trim(), ignoreCase = true), input.trim())
    }

    private fun popUp(text: String) {
        pop = text
        popId++
    }

    private fun answer(correct: Boolean, response: String) {
        val w = word ?: return
        if (phase != FishPhase.PLAYING) return
        answered = correct
        val ms = System.currentTimeMillis() - questionAt
        // A 객관식 answer in under a second is a guess: it gives no time.
        val guessed = mode == StudyMode.CHOICE && ms < StudyViewModel.GUESS_MS
        total++
        if (correct) {
            right++
            if (guessed) {
                popUp("너무 빨라요! +0초")
            } else {
                combo++
                val bonus = fishing.right + (if (mode == StudyMode.CHOICE) 0.0 else Fishing.TYPING_EXTRA) +
                    (if (combo % 3 == 0) Fishing.COMBO_BONUS else 0.0)
                seconds += bonus
                popUp((if (combo % 3 == 0) "릴 감기! " else "") + "+${bonus.toInt()}초")
                if (settings.soundEffects) {
                    if (combo >= 3) sfx.combo(settings.comboSound, combo) else tone?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120)
                }
            }
        } else {
            combo = 0
            seconds -= fishing.wrong
            missed += w
            pullId++
            popUp("물고기가 당겨요! -${fishing.wrong.toInt()}초")
        }
        answerLog.add(AnswerEntry(System.currentTimeMillis(), w.id, w.text, w.pos, mode, correct, false, if (correct) "" else response, ms))
        if (!guessed) {
            wrongNote.record(w.id, correct)
            stats.recordAnswer(w.text, correct)
        }
        when {
            seconds >= goal -> win()
            seconds <= 0 -> lose()
            else -> {
                val t = turn
                viewModelScope.launch {
                    // Wrong answers stay a moment longer so the right one can be read.
                    delay(if (correct) 600 else 1600)
                    if (turn == t) nextQuestion()
                }
            }
        }
    }

    /** The 다음 button on a wrong answer, for those who don't want to wait. */
    fun next() {
        if (answered != null) nextQuestion()
    }

    private fun finishGame() {
        durationMs = System.currentTimeMillis() - startedAt
        stats.addStudyMs(durationMs)
        word = null
    }

    private fun win() {
        seconds = goal
        phase = FishPhase.WON
        finishGame()
        val accuracy = if (total == 0) 0.0 else right / total.toDouble()
        val speed = ((240_000.0 - durationMs) / 180_000.0).coerceIn(0.0, 1.0)
        kg = Fishing.weight(fish, accuracy * 0.7 + speed * 0.3)
        newRecord = fishing.recordCatch(fish, kg)
        game.addStars(Fishing.CATCH_STARS)
        if (!game.hasStamp()) game.stampToday()
        if (settings.soundEffects) tone?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
        report(true)
    }

    private fun lose() {
        if (phase != FishPhase.PLAYING) return
        seconds = 0.0
        phase = FishPhase.LOST
        finishGame()
        triesLeft = fishing.triesLeft
        report(false)
    }

    private fun report(caught: Boolean) {
        if (settings.telegramToken.isBlank() || settings.telegramChatId.isBlank()) return
        val text = buildString {
            append("🎣 단어 낚시 도전!\n\n")
            if (caught) append("${fish.emoji} ${fish.name} ${Fishing.formatKg(kg)}을(를) 낚았어요!" + (if (newRecord) " 🏆 최고 기록" else "") + "\n")
            else append("😢 ${fish.name}이(가) 도망갔어요${fishing.triesNote}\n")
            append("정답 $right/$total · ${durationMs / 60_000}분 ${durationMs / 1000 % 60}초\n")
            if (missed.isNotEmpty()) {
                append("\n❗ 틀린 단어: ")
                append(missed.distinctBy { it.id }.joinToString(", ") { "${it.text}(${it.meaning.substringBefore(" /")})" })
            }
        }
        viewModelScope.launch { Telegram.send(settings.telegramToken, settings.telegramChatId, text) }
    }

    fun backToIntro() {
        phase = FishPhase.INTRO
        refreshLock()
    }

    override fun onCleared() {
        // Leaving mid-game counts as letting the fish go; the time still counts as study.
        if (phase == FishPhase.PLAYING) {
            durationMs = System.currentTimeMillis() - startedAt
            stats.addStudyMs(durationMs)
        }
        tone?.release()
        sfx.release()
    }
}

@Composable
fun FishingScreen(onBack: () -> Unit, vm: FishingViewModel = viewModel()) {
    val tts = rememberTts()
    var showBook by remember { mutableStateOf(false) }
    if (showBook) FishBook(vm.fishing) { showBook = false }
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("🎣 단어 낚시 도전!") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                },
                actions = { TextButton(onClick = { showBook = true }) { Text("📖 도감") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (vm.phase) {
                FishPhase.INTRO -> Intro(vm, onBook = { showBook = true })
                FishPhase.PLAYING -> Playing(vm, tts)
                FishPhase.WON -> Result(vm, won = true, onBack = onBack, onBook = { showBook = true })
                FishPhase.LOST -> Result(vm, won = false, onBack = onBack, onBook = { showBook = true })
            }
        }
    }
}

@Composable
private fun Intro(vm: FishingViewModel, onBook: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshLock() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("🌊🎣🐋", fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Text(
            "오늘의 끝판왕",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val f = vm.fishing
            Text("이렇게 해요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("⏱ 낚싯줄 시간이 ${f.start.toInt()}초에서 시작해 계속 줄어요")
            Text("✅ 맞히면 +${f.right.toInt()}초 (빈칸·듣고 쓰기는 +${(f.right + Fishing.TYPING_EXTRA).toInt()}초)")
            Text("🔥 3개 연속 맞히면 '릴 감기!' +${Fishing.COMBO_BONUS.toInt()}초 더")
            Text("❌ 틀리면 -${f.wrong.toInt()}초, 너무 빨리 찍으면 0초")
            Text("🐋 ${f.goal.toInt()}초가 되면 물고기를 낚아요! 0초가 되면 도망가요")
            Text("📈 물고기에 가까워질수록 세게 당겨서 시간이 빨리 줄어요")
            Text("🎯 낚으면 ⭐${Fishing.CATCH_STARS}개 + 오늘 공부 끝!" + vm.fishing.triesNote)
        }
        Spacer(Modifier.height(16.dp))
        val reason = vm.lockReason
        if (reason != null) {
            Text(
                "🔒 $reason",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PrimaryButton("🎣 낚시 시작" + vm.fishing.triesNote, vm::start)
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("📖 물고기 도감", onBook)
    }
}

@Composable
private fun ColumnScope.Playing(vm: FishingViewModel, tts: TextToSpeech?) {
    val w = vm.word ?: return
    LaunchedEffect(w.id, vm.mode) {
        if (vm.mode == StudyMode.DICTATION) tts?.speak(w.text, TextToSpeech.QUEUE_FLUSH, null, "f${w.id}")
    }
    SeaScene(vm)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { (vm.seconds / vm.goal).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(10.dp),
            color = if (vm.seconds < 20) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            "  ${vm.seconds.toInt()}초 / ${vm.goal.toInt()}초" + if (vm.combo >= 2) " 🔥${vm.combo}" else "",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (vm.mode == StudyMode.CHOICE) {
            ChoiceQuiz(
                word = w,
                choices = vm.choices,
                reverse = false,
                answered = vm.answered,
                pickedId = vm.pickedId,
                onPick = vm::pick,
                onNext = vm::next,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TypingQuiz(
                word = w,
                pattern = vm.pattern,
                dictation = vm.mode == StudyMode.DICTATION,
                answered = vm.answered,
                onSubmit = vm::submit,
                onNext = vm::next,
                onSpeak = { tts?.speak(w.text, TextToSpeech.QUEUE_FLUSH, null, "f${w.id}") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The sea: the rod on the left, the fish coming closer as the line fills, a jerk on wrong answers. */
@Composable
private fun SeaScene(vm: FishingViewModel) {
    val closeness by animateFloatAsState((vm.seconds / vm.goal).toFloat().coerceIn(0f, 1f), tween(300), label = "fish")
    val jerk = remember { Animatable(0f) }
    LaunchedEffect(vm.pullId) {
        if (vm.pullId == 0) return@LaunchedEffect
        repeat(3) {
            jerk.animateTo(14f, tween(50))
            jerk.animateTo(-14f, tween(50))
        }
        jerk.animateTo(0f, tween(50))
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF7FC8F8), Color(0xFF1E5AA8), Color(0xFF0B2A5B)))),
    ) {
        val width = maxWidth
        // The fish swims from the far right towards the boat as the line fills.
        val fishX = 64.dp + (width - 120.dp) * (1f - closeness)
        val fishSize = (34 + 30 * closeness).sp
        Canvas(Modifier.fillMaxSize()) {
            val rodTip = Offset(58.dp.toPx(), 26.dp.toPx())
            val fishAt = Offset((fishX + 20.dp).toPx() + jerk.value, 95.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.85f), rodTip, fishAt, strokeWidth = 2.dp.toPx())
        }
        Text("🚣", fontSize = 34.sp, modifier = Modifier.offset(x = 10.dp, y = 6.dp))
        Text(
            vm.fish.emoji,
            fontSize = fishSize,
            modifier = Modifier.offset(x = fishX + jerk.value.dp, y = 72.dp),
        )
        PopText(vm.pop, vm.popId, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        Text(
            "${vm.fish.name} 낚는 중",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}

@Composable
private fun PopText(text: String?, id: Int, modifier: Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(id) {
        if (id == 0) return@LaunchedEffect
        alpha.snapTo(1f)
        delay(700)
        alpha.animateTo(0f, tween(300))
    }
    if (text != null && alpha.value > 0f) {
        Text(
            text,
            color = Color.White.copy(alpha = alpha.value),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier.background(Color.Black.copy(alpha = 0.25f * alpha.value), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Result(vm: FishingViewModel, won: Boolean, onBack: () -> Unit, onBook: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(if (won) vm.fish.emoji else "🌊", fontSize = 80.sp)
        Spacer(Modifier.height(8.dp))
        if (won) {
            Text("${vm.fish.name} ${Fishing.formatKg(vm.kg)}!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (vm.newRecord) Text("🏆 최고 기록이에요!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("⭐${Fishing.CATCH_STARS}개를 받았어요", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("🎣 오늘 공부 끝! 정말 잘했어요. 내일 또 만나요", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        } else {
            Text("물고기가 도망갔어요 😢", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!vm.fishing.unlimited) Text("남은 도전 ${vm.triesLeft}번", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "정답 ${vm.right}/${vm.total} · ${vm.durationMs / 60_000}분 ${vm.durationMs / 1000 % 60}초",
            style = MaterialTheme.typography.bodyLarge,
        )
        val missed = vm.missed.distinctBy { it.id }
        if (missed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("틀린 단어", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            missed.forEach { Text("${it.text}  ${it.meaning.substringBefore(" /")}", style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(24.dp))
        if (vm.triesLeft > 0) {
            PrimaryButton(if (won) "🎣 한 번 더" else "🎣 다시 도전", vm::start)
            Spacer(Modifier.height(8.dp))
        }
        SecondaryButton("📖 물고기 도감", onBook)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("홈으로", onBack)
    }
}

/** 물고기 도감: every fish, how many were caught and the heaviest; locked ones show the level they need. */
@Composable
fun FishBook(fishing: Fishing, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📖 물고기 도감") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "레벨이 오를수록 더 큰 물고기가 나와요. 잘 맞히고 빨리 낚을수록 무거워요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Fishing.species.forEach { f: Fish ->
                    val count = fishing.count(f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (count > 0) f.emoji else "❓", fontSize = 30.sp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                if (count > 0) f.name else "Lv.${f.level}부터 나와요",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (count > 0) "${count}마리 · 최고 ${Fishing.formatKg(fishing.best(f))}" else "아직 못 낚았어요",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
