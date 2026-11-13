package com.wordbook.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordbook.app.data.LockCard
import com.wordbook.app.data.LockScreen
import com.wordbook.app.data.Srs
import com.wordbook.app.data.Word
import com.wordbook.app.ui.theme.WordBookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The big card above the lock screen (B안): a few words, studied with the app's swipes
 * (위 = 외웠어요, 왼쪽 = 헷갈려요, 아래 = 몰라요, 톡 = 뜻 보기). '닫고 폰 쓰기' or the back button
 * goes to the phone's own lock screen.
 */
class LockWordActivity : ComponentActivity() {
    private var session by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        setContent {
            WordBookTheme {
                LockWordScreen(session, onClose = { close() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Screen went off again while open: start over with fresh words.
        session++
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        close()
    }

    private fun close() {
        LockScreen.markShown(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

@Composable
private fun LockWordScreen(session: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf<List<Word>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(session) {
        words = withContext(Dispatchers.IO) { LockCard.pickWords(context, LockScreen.count(context)) }
        index = 0; open = false
    }
    val list = words
    // Nothing to study, or all done: go straight to the phone's lock screen.
    LaunchedEffect(list, index) {
        if (list != null && index >= list.size) onClose()
    }
    val answer: (Srs.Result) -> Unit = { result ->
        val w = list?.getOrNull(index)
        if (w != null) {
            scope.launch(Dispatchers.IO) { LockCard.record(context, w.id, result) }
            index++; open = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16305A), Color(0xFF2E6AA8), Color(0xFF5A9BEE)))),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            val total = list?.size ?: 0
            Text(
                "🎣 낚아보카 · ${minOf(index + 1, maxOf(total, 1))} / $total",
                color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp,
            )
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else index.toFloat() / total },
                modifier = Modifier.fillMaxWidth(0.7f).padding(top = 8.dp),
                color = Color(0xFF7FD8C9), trackColor = Color.White.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(18.dp))
            Text("⬆ 외웠어요", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            val word = list?.getOrNull(index)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (word != null) {
                    SwipeCard(word, open, onTap = { open = !open }, onAnswer = answer)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⬅ 헷갈려요", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Text("⬇ 몰라요", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            // Buttons too, for anyone who prefers tapping to swiping.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("몰라요", Color(0xFFFDE8E6), Srs.Result.WRONG),
                    Triple("헷갈려요", Color(0xFFFFF1DC), Srs.Result.RIGHT),
                    Triple("외웠어요", Color(0xFFE2F5EF), Srs.Result.EASY),
                ).forEach { (label, bg, result) ->
                    TextButton(
                        onClick = { answer(result) },
                        modifier = Modifier.weight(1f).background(bg, RoundedCornerShape(12.dp)),
                    ) { Text(label, color = Color(0xFF2B3A4F), fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White),
                shape = RoundedCornerShape(24.dp),
            ) { Text("🔓 닫고 폰 쓰기", fontSize = 16.sp) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SwipeCard(word: Word, open: Boolean, onTap: () -> Unit, onAnswer: (Srs.Result) -> Unit) {
    val scope = rememberCoroutineScope()
    val dx = remember(word.id) { Animatable(0f) }
    val dy = remember(word.id) { Animatable(0f) }
    val limit = 220f
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        contentColor = Color(0xFF2B3A4F),
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .fillMaxHeight()
            .graphicsLayer {
                translationX = dx.value; translationY = dy.value
                rotationZ = dx.value / 40f
            }
            .pointerInput(word.id) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(word.id) {
                detectDragGestures(
                    onDragEnd = {
                        val x = dx.value; val y = dy.value
                        val result = when {
                            y < -limit && abs(y) > abs(x) -> Srs.Result.EASY
                            y > limit && abs(y) > abs(x) -> Srs.Result.WRONG
                            x < -limit && abs(x) > abs(y) -> Srs.Result.RIGHT
                            else -> null
                        }
                        if (result != null) {
                            onAnswer(result)
                        } else {
                            scope.launch { dx.animateTo(0f) }
                            scope.launch { dy.animateTo(0f) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dx.animateTo(0f) }
                        scope.launch { dy.animateTo(0f) }
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { dx.snapTo(dx.value + drag.x) }
                    scope.launch { dy.snapTo(dy.value + drag.y) }
                }
            },
    ) {
        // Always the light colours on the white card, even when the phone is in dark mode.
        androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme()) {
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(word.text, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2B3A4F), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            if (open) {
                PosMeaning(word.pos, word.meaning, fontSize = 22.sp, badgeSize = 13.sp)
                if (word.example.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    ExampleText(word.example, fontSize = 14.sp)
                }
            } else {
                Text("톡 누르면 뜻이 보여요", fontSize = 16.sp, color = Color(0xFF8A97AB))
            }
        }
    }
}
}
