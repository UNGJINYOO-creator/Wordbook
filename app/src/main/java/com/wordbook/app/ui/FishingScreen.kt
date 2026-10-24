package com.wordbook.app.ui

import com.wordbook.app.data.Giant
import com.wordbook.app.data.GiantFish
import com.wordbook.app.data.GiantState

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.wordbook.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
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

enum class FishPhase { INTRO, PLAYING, WON, LOST, REVIEW }

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
    /** Words the questions are about: [pool] in 오늘의 바다, only the newly learned words in 새 바다. */
    private var questions = listOf<Word>()
    private var newSeaWords = listOf<Word>()
    val newSeaCount: Int get() = newSeaWords.size
    /** true while playing (or just finished) in the 🌊 새 바다. */
    var newSea by mutableStateOf(false)
        private set
    val catchStars: Int get() = if (giantMode) Giant.CATCH_GIFT else Fishing.CATCH_STARS + if (newSea) Fishing.NEW_SEA_BONUS else 0
    private val giantStore = Giant(app)
    /** The 거대 물고기 hunt: the current giant's health, read when the screen opens and after each game. */
    var giant by mutableStateOf<GiantState?>(null)
        private set
    /** true while playing (or just finished) a 거대 물고기 final battle. */
    var giantMode by mutableStateOf(false)
        private set
    var giantReview by mutableStateOf(giantStore.reviewNeeded)
        private set
    /** The giant just caught, for the result screen. */
    var caughtGiant by mutableStateOf<GiantFish?>(null)
        private set

    private suspend fun refreshGiant() {
        giant = giantStore.state(dao.getAllWords(), stats.learnedToday)
    }
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
    /** How many new words the 새 단어 더 배우기 button brings (the daily word goal). */
    val newWords: Int get() = StudyViewModel.newPerDay(settings)

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
    /** 듣고 쓰기 was passed for the rest of this game: those questions become meaning + full spelling. */
    private var listenOff = false
    var modeNote by mutableStateOf<String?>(null)
        private set
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
    /** The "5m 끌어왔어요" / "+3m" pop over the sea, and a counter so the same text pops again. */
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
            val fresh = fishing.newSeaIds(stats.learnedToday)
            newSeaWords = all.filter { it.id in fresh }.distinctBy { it.text.lowercase() }
                .takeIf { it.size >= Fishing.NEW_SEA_MIN } ?: emptyList()
            refreshGiant()
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

    fun start(inNewSea: Boolean = newSea) {
        refreshLock()
        if (lockReason != null) return
        giantMode = false
        newSea = inNewSea && newSeaWords.isNotEmpty()
        questions = if (newSea) newSeaWords else pool
        fishing.useTry()
        triesLeft = fishing.triesLeft
        fish = Fishing.fishFor(game.level)
        seconds = fishing.start
        begin()
    }

    /** The 거대 물고기 final battle: the block's words only, from further out; it uses no daily try. */
    fun startGiant() {
        val g = giant ?: return
        val f = g.fish ?: return
        if (!g.ready) return
        giantMode = true
        newSea = false
        questions = g.words.filter { !it.excluded && it.meaning.isNotBlank() }.ifEmpty { g.words }
        fish = Fish(0, f.emoji, f.name, f.minKg, f.maxKg)
        seconds = maxOf(1.0, goal - Giant.FINAL_START)
        begin()
    }

    /** After a lost final battle: the missed words were looked at, so try again. */
    fun finishReview() {
        giantStore.reviewNeeded = false
        giantReview = false
        startGiant()
    }

    fun openReview() {
        if (missed.isEmpty()) finishReview() else phase = FishPhase.REVIEW
    }

    private fun begin() {
        caughtGiant = null
        combo = 0; right = 0; total = 0
        missed.clear()
        recent.clear()
        listenOff = false
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
        val w = questions.filter { it.id !in recent }.randomOrNull() ?: questions.random()
        recent.addLast(w.id)
        if (recent.size > minOf(6, questions.size - 1)) recent.removeFirst()
        word = w
        val roll = Math.random()
        val muted = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0
        mode = when {
            roll < 0.6 -> StudyMode.CHOICE
            roll < 0.85 || muted -> StudyMode.SPELLING
            else -> StudyMode.DICTATION
        }
        modeNote = null
        if (mode == StudyMode.DICTATION && listenOff) {
            mode = StudyMode.SPELLING
            pattern = w.text.map { if (it.isLetter()) '_' else it }.joinToString("")
            modeNote = "🔇 뜻을 보고 스펠링 전체를 쓰세요"
            return
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

    /** "지금 들을 수 없어요": the same word with its meaning shown and every letter blank, not counted as wrong. */
    fun passDictation() {
        val w = word ?: return
        if (mode != StudyMode.DICTATION || answered != null) return
        if (settings.dictationPass == com.wordbook.app.data.DictationPass.SESSION) listenOff = true
        mode = StudyMode.SPELLING
        pattern = w.text.map { if (it.isLetter()) '_' else it }.joinToString("")
        modeNote = "🔇 들을 수 없어서 뜻을 보고 스펠링 전체 쓰기로 바꿨어요"
        questionAt = System.currentTimeMillis()
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
                popUp("너무 빨라요! 0m")
            } else {
                combo++
                val bonus = fishing.right + (if (mode == StudyMode.CHOICE) 0.0 else Fishing.TYPING_EXTRA) +
                    (if (combo % 3 == 0) Fishing.COMBO_BONUS else 0.0)
                seconds += bonus
                popUp((if (combo % 3 == 0) "🔥 릴 감기! " else "🎣 ") + "${bonus.toInt()}m 끌어왔어요!")
                if (settings.soundEffects) {
                    if (combo >= 3) sfx.combo(settings.comboSound, combo) else tone?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120)
                }
            }
        } else {
            combo = 0
            seconds -= fishing.wrong
            missed += w
            pullId++
            popUp("😱 물고기가 도망치고 있어요! +${fishing.wrong.toInt()}m")
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
        val g = giant
        if (giantMode && g != null) {
            giantStore.recordCatch(g.index, kg)
            caughtGiant = g.fish
            newRecord = false
        } else {
            newRecord = fishing.recordCatch(fish, kg)
            fishing.markSeen(stats.learnedToday)
            // Today's words land their hit on the giant once the day's fish is in.
            giantStore.confirm(stats.learnedToday)
        }
        game.addStars(catchStars)
        viewModelScope.launch { refreshGiant() }
        if (!game.hasStamp()) game.stampToday()
        if (settings.soundEffects) tone?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
        report(true)
    }

    private fun lose() {
        if (phase != FishPhase.PLAYING) return
        seconds = 0.0
        phase = FishPhase.LOST
        finishGame()
        if (giantMode) {
            giantStore.reviewNeeded = true
            giantReview = true
        }
        triesLeft = fishing.triesLeft
        report(false)
    }

    private fun report(caught: Boolean) {
        if (settings.telegramToken.isBlank() || settings.telegramChatId.isBlank()) return
        val text = buildString {
            append(if (giantMode) "🏆 거대 물고기 최종전!\n\n" else "🎣 오늘의 단어 낚시 도전!\n\n")
            if (caught) append("${fish.emoji} ${fish.name} ${Fishing.formatKg(kg)}을(를) 낚았어요!" + (if (newRecord) " 🏆 최고 기록" else "") + "\n")
            else append("😢 줄이 끊어져서 ${fish.name}이(가) 도망갔어요${fishing.triesNote}\n")
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
fun FishingScreen(onBack: () -> Unit, onMoreWords: () -> Unit = {}, vm: FishingViewModel = viewModel()) {
    val tts = rememberTts()
    var showBook by remember { mutableStateOf(false) }
    if (showBook) FishBook(vm.fishing, vm.giant) { showBook = false }
    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("🎣 오늘의 단어 낚시 도전!") },
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
                FishPhase.WON -> Result(vm, won = true, onBack = onBack, onBook = { showBook = true }, onMoreWords = onMoreWords)
                FishPhase.LOST -> Result(vm, won = false, onBack = onBack, onBook = { showBook = true }, onMoreWords = onMoreWords)
                FishPhase.REVIEW -> GiantReview(vm, tts)
            }
        }
    }
}

@Composable
private fun Intro(vm: FishingViewModel, onBook: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshLock() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        vm.giant?.let { GiantPanel(it, vm) }
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
            Text("🐟 물고기가 ${(f.goal - f.start).toInt()}m 앞에서 시작해요. 가만히 있으면 조금씩 도망가요")
            Text("✅ 맞히면 ${f.right.toInt()}m 끌어와요 (빈칸·듣고 쓰기는 ${(f.right + Fishing.TYPING_EXTRA).toInt()}m)")
            Text("🔥 3개 연속 맞히면 '릴 감기!' ${Fishing.COMBO_BONUS.toInt()}m 더")
            Text("❌ 틀리면 ${f.wrong.toInt()}m 멀어져요, 너무 빨리 찍으면 0m")
            Text("🐋 0m가 되면 낚아요! ${f.goal.toInt()}m까지 멀어지면 줄이 끊어져요")
            Text("📈 가까워질수록 물고기가 세게 버텨서 더 빨리 도망가요")
            Text("🎯 낚으면 선물 물고기 ${Fishing.CATCH_STARS}마리! (🌊 새 바다는 ${Fishing.CATCH_STARS + Fishing.NEW_SEA_BONUS}마리)" + vm.fishing.triesNote)
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
        } else if (vm.newSeaCount > 0) {
            // After 새 단어 더 배우기: fish the words just learned, or the usual mix.
            Text("어느 바다에서 낚을까요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("🌊 새 바다 · 방금 배운 ${vm.newSeaCount}단어 (🐟${Fishing.CATCH_STARS + Fishing.NEW_SEA_BONUS})", { vm.start(true) })
            Spacer(Modifier.height(8.dp))
            SecondaryButton("🏝 오늘의 바다 · 오늘 + 예전 단어 (🐟${Fishing.CATCH_STARS})", { vm.start(false) })
            if (vm.fishing.triesNote.isNotEmpty()) {
                Text(vm.fishing.triesNote.trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            PrimaryButton("🎣 낚시 시작" + vm.fishing.triesNote, { vm.start(false) })
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
    SeaBand(vm)
    // The question card rides up over the bottom of the blue band.
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .offset(y = (-26).dp)
            .padding(horizontal = 14.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
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
        } else key(vm.mode) {
            vm.modeNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
            }
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
            if (vm.mode == StudyMode.DICTATION && vm.answered == null) {
                TextButton(onClick = vm::passDictation, modifier = Modifier.fillMaxWidth()) {
                    Text("🔇 지금 들을 수 없어요 · 뜻 보고 쓰기")
                }
            }
        }
    }
}

/**
 * The blue band on top: distance to the fish to a tenth of a metre, the angler on the left and the fish coming
 * closer along the line as it fills, with a jerk on wrong answers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeaBand(vm: FishingViewModel) {
    val closeness by animateFloatAsState((vm.seconds / vm.goal).toFloat().coerceIn(0f, 1f), tween(300), label = "fish")
    val jerk = remember { Animatable(0f) }
    LaunchedEffect(vm.pullId) {
        if (vm.pullId == 0) return@LaunchedEffect
        repeat(3) {
            jerk.animateTo(10f, tween(50))
            jerk.animateTo(-10f, tween(50))
        }
        jerk.animateTo(0f, tween(50))
    }
    // Shown as the distance to the fish: 0m lands it, the goal distance snaps the line.
    val distance = (vm.goal - vm.seconds).coerceIn(0.0, vm.goal)
    val danger = distance > vm.goal * 0.85
    // With the keyboard up (빈칸, 듣고 쓰기) the band shrinks so the question still fits.
    val compact = WindowInsets.isImeVisible
    // Eased instead of switched, so the question card glides up with the keyboard rather than jumping.
    val k by animateFloatAsState(if (compact) 1f else 0f, tween(250), label = "compact")
    fun dp(wide: Int, narrow: Int) = androidx.compose.ui.unit.lerp(wide.dp, narrow.dp, k)
    fun sp(wide: Int, narrow: Int) = (wide + (narrow - wide) * k).sp
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF0B3C9E), Color(0xFF1E6BFF), Color(0xFF33B5FF))),
                RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
            )
            .padding(start = 16.dp, end = 16.dp, top = dp(10, 6), bottom = dp(38, 32)),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🐟 물고기까지 ${"%.1f".format(distance)}m",
                    color = if (danger) Color(0xFFFFB4B4) else Color.White,
                    fontSize = sp(22, 18),
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                if (vm.combo >= 2) Text("🔥 콤보 ${vm.combo}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(dp(10, 4)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.angler),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(dp(56, 36))
                        .clip(RoundedCornerShape(dp(16, 10)))
                        .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(dp(16, 10))),
                )
                BoxWithConstraints(Modifier.weight(1f).height(dp(56, 36))) {
                    val fishSize = dp(34, 26)
                    // The fish starts at the far right and swims to the angler as the line fills.
                    val fishX = 6.dp + (maxWidth - fishSize - 6.dp) * (1f - closeness)
                    Canvas(Modifier.fillMaxSize()) {
                        val y = size.height / 2
                        val end = (fishX + 4.dp).toPx() + jerk.value
                        drawLine(
                            Color.White.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                        )
                        drawLine(Color(0xFF9FE3FF), Offset(0f, y), Offset(end, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                    }
                    Text(
                        vm.fish.emoji,
                        fontSize = sp(30, 22),
                        modifier = Modifier.align(Alignment.CenterStart).offset(x = fishX + jerk.value.dp),
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(!compact) {
            Row(Modifier.padding(top = 8.dp)) {
                Text(
                    when {
                        danger -> "⚠️ 줄이 끊어지려 해요!"
                        distance < 20 -> "거의 다 왔어요! 조금만 더!"
                        else -> "🎣 ${vm.fish.name} 낚는 중"
                    },
                    color = if (danger) Color(0xFFFFB4B4) else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (danger || distance < 20) FontWeight.Bold else null,
                    modifier = Modifier.weight(1f),
                )
                Text("${vm.goal.toInt()}m면 줄이 끊어져요", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            }
            }
        }
        PopText(vm.pop, vm.popId, Modifier.align(Alignment.TopCenter).padding(top = 34.dp))
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
private fun Result(vm: FishingViewModel, won: Boolean, onBack: () -> Unit, onBook: () -> Unit, onMoreWords: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(if (won) vm.fish.emoji else "🌊", fontSize = 80.sp)
        Spacer(Modifier.height(8.dp))
        val giantFish = vm.caughtGiant
        if (won && giantFish != null) {
            Text("🏆 거대 물고기를 낚았어요!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("${giantFish.name} ${Fishing.formatKg(vm.kg)}!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(giantFish.english, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text("💡 ${giantFish.fact}", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text("선물 물고기 ${vm.catchStars}마리를 받았어요 🐟 · 도감에 올렸어요", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        } else if (won) {
            Text("${vm.fish.name} ${Fishing.formatKg(vm.kg)}!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (vm.newRecord) Text("🏆 최고 기록이에요!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("선물 물고기 ${vm.catchStars}마리를 받았어요 🐟" + if (vm.newSea) " (🌊 새 바다 보너스!)" else "", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("🎣 물고기를 낚았어요! 정말 잘했어요", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        } else {
            Text("줄이 끊어져서 도망갔어요 😢", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!vm.fishing.unlimited && !vm.giantMode) Text("남은 도전 ${vm.triesLeft}번", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
        if (vm.giantMode && !won) {
            PrimaryButton("📖 틀린 단어 복습하고 다시 도전", vm::openReview)
            Spacer(Modifier.height(12.dp))
        }
        // Riding the good mood after a catch: straight into the next new words.
        PrimaryButton("📚 새 단어 ${vm.newWords}개 더 배우기", onMoreWords)
        Text(
            "아직 안 배운 단어가 나와요. 맞힌 만큼 레벨이 올라가요!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (vm.triesLeft > 0 && !vm.giantMode) {
            SecondaryButton(if (won) "🎣 한 번 더" else "🎣 다시 도전", { vm.start() })
            Spacer(Modifier.height(8.dp))
        }
        SecondaryButton("📖 물고기 도감", onBook)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("홈으로", onBack)
    }
}

/** 물고기 도감: every fish, how many were caught and the heaviest; locked ones show the level they need. */
@Composable
fun FishBook(fishing: Fishing, giant: GiantState?, onDismiss: () -> Unit) {
    val store = Giant(LocalContext.current)
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
                Spacer(Modifier.height(8.dp))
                Text("🏆 거대 물고기 ${giant?.caught ?: 0}/${Giant.species.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "중학 영단어 100개를 외울 때마다 한 마리씩 사냥할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Giant.species.forEachIndexed { i, g ->
                    val at = store.caughtAt(i)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (at > 0) g.emoji else "❓", fontSize = 30.sp)
                        Column(Modifier.padding(start = 12.dp)) {
                            if (at > 0) {
                                Text("${g.name} · ${g.english}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(java.util.Date(at)) +
                                        " · ${Fishing.formatKg(store.kg(i))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text("💡 ${g.fact}", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("${i + 1}번째 거대 물고기", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "단어 ${i * Giant.BLOCK + 1}~${(i + 1) * Giant.BLOCK}번을 외우면 만날 수 있어요" +
                                        if (giant?.index == i) " · 지금 사냥 중!" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                val tropical = giant?.tropical ?: 0
                Spacer(Modifier.height(8.dp))
                Text("🌴 열대 바다 $tropical", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "직접 넣은 단어를 100개 외울 때마다 열대 물고기가 한 마리씩 찾아와요" +
                        " (다음까지 ${Giant.BLOCK - (giant?.extraLearned ?: 0) % Giant.BLOCK}단어).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (tropical > 0) {
                    Text(
                        (0 until tropical).joinToString("  ") { Giant.tropical[it % Giant.tropical.size] },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** The 거대 물고기 hunt on the fishing intro: the giant's health and, at 0, the final battle. */
@Composable
private fun GiantPanel(g: GiantState, vm: FishingViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp)
            .background(Color(0xFF0B3C9E), RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        val f = g.fish
        if (f == null) {
            Text("🏆 거대 물고기 18마리를 모두 낚았어요!", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            return@Column
        }
        Text("🏆 거대 물고기 사냥 ${g.index + 1}/${Giant.species.size}", color = Color(0xFF9FE3FF), style = MaterialTheme.typography.labelLarge)
        Text("${f.emoji} ${f.name}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ThickProgress(g.hp / g.total.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth())
        Text("체력 ${g.hp}/${g.total}", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        when {
            g.ready -> {
                Text("지쳐서 떠올랐어요! 지금 끌어올릴 수 있어요", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    if (vm.giantReview) "📖 틀린 단어 보고 다시 도전" else "🏆 최종전: ${f.name} 끌어올리기!",
                    { if (vm.giantReview) vm.openReview() else vm.startGiant() },
                )
            }
            g.pending > 0 -> Text(
                "오늘 외운 ${g.pending}단어 → 오늘 낚시에 성공하면 체력 -${g.pending}!",
                color = Color(0xFFFFE08A),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            else -> Text("단어를 외울수록 체력이 깎여요. 0이 되면 최종전!", color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** After a lost final battle: look at each missed word once, then try again. */
@Composable
private fun GiantReview(vm: FishingViewModel, tts: TextToSpeech?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("📖 틀린 단어 복습", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "하나씩 눌러 발음을 들어 보고 뜻을 읽어 보세요. 다 보면 바로 다시 도전할 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        vm.missed.distinctBy { it.id }.forEach { w ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                    .clickable { tts?.speak(w.text, TextToSpeech.QUEUE_FLUSH, null, "r${w.id}") }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔊", fontSize = 20.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(w.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(w.meaning, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("다 봤어요! 다시 도전 🏆", vm::finishReview)
    }
}
