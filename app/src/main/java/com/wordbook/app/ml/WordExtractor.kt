package com.wordbook.app.ml

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Runs on-device OCR (Korean + Latin) on a photo and pairs headwords with their Korean meanings. */
object WordExtractor {
    @Volatile private var dictionary: Set<String>? = null

    /** Raw OCR lines of the last photo, shown on the scan screen to diagnose missed words. */
    @Volatile var lastLines: List<OcrLine> = emptyList()
        private set

    /** The English spelling list (assets/words.txt). */
    suspend fun dictionary(context: Context): Set<String> =
        dictionary ?: withContext(Dispatchers.IO) {
            context.assets.open("words.txt").bufferedReader().useLines { it.toHashSet() }
        }.also { dictionary = it }

    private fun isWord(dict: Set<String>, w: String) = SpellFix.isWord(dict, w)

    /** Size of the last photo as the recognizer saw it (after rotation), for cutting word strips out of it. */
    @Volatile var lastSize: Pair<Int, Int> = 0 to 0
        private set

    /**
     * How the last photo was read, for the scan screen: "" for the on-device reader, else a note such as
     * "AI가 읽었어요" or why the AI couldn't be used.
     */
    @Volatile var lastNote: String = ""
        private set
    /** True when the AI read the last photo. */
    @Volatile var lastByAi: Boolean = false
        private set

    suspend fun extract(context: Context, uri: Uri): PairResult {
        val dict = dictionary(context)
        val image = InputImage.fromFilePath(context, uri)
        lastSize = image.width to image.height
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val lines = try {
            recognizer.process(image).await().textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrLine(line.text, box.left, box.top, box.right, box.bottom, line.confidence)
                }
            }
        } finally {
            recognizer.close()
        }
        lastLines = lines.sortedWith(compareBy({ it.top }, { it.left }))
        val local = VocabPairer(correct = { SpellFix.fix(dict, it) }) { isWord(dict, it) }.pairWithDropped(lines)
        lastByAi = false
        lastNote = ""
        if (!AiKey.ready(context)) return local
        return try {
            readWithAi(context, uri, image.width, image.height, local, lines)
        } catch (e: Exception) {
            lastNote = "AI가 읽다가 문제가 생겨서 기존 방식으로 읽었어요."
            local
        }
    }

    private suspend fun readWithAi(context: Context, uri: Uri, width: Int, height: Int, local: PairResult, lines: List<OcrLine>): PairResult {
        // The AI reads the page; the on-device lines above still give where each word is in the photo.
        val photo = com.wordbook.app.ui.PhotoCrops.open(context, uri, width, height, 2048)?.first
        if (photo == null) {
            lastNote = "사진을 AI에 보낼 수 없어서 기존 방식으로 읽었어요."
            return local
        }
        val jpeg = withContext(Dispatchers.Default) { try { GeminiVision.encode(photo) } finally { photo.recycle() } }
        return when (val r = GeminiVision.read(AiKey.get(context), jpeg)) {
            is GeminiVision.Outcome.Words -> {
                if (r.words.isEmpty()) {
                    lastNote = "AI가 표제어를 찾지 못해서 기존 방식으로 읽었어요."
                    local
                } else {
                    lastByAi = true
                    lastNote = if (r.model.contains("lite")) "AI(라이트)가 읽었어요" else "AI가 읽었어요"
                    PairResult(AiMerge.merge(r.words, local, lines), emptyList())
                }
            }
            is GeminiVision.Outcome.Failed -> {
                lastNote = if ("기존 방식" in r.reason) r.reason else "${r.reason} 기존 방식으로 읽었어요."
                local
            }
        }
    }
}
