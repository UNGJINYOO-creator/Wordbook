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

    private suspend fun dictionary(context: Context): Set<String> =
        dictionary ?: withContext(Dispatchers.IO) {
            context.assets.open("words.txt").bufferedReader().useLines { it.toHashSet() }
        }.also { dictionary = it }

    /** Accepts dictionary words and common inflections (plurals, -ed, -ing...). */
    private fun isWord(dict: Set<String>, w: String): Boolean {
        if (w in dict) return true
        val stems = listOf("s", "es", "ed", "d", "ing", "ly", "er", "est")
            .filter { w.length > it.length + 2 && w.endsWith(it) }
            .map { w.dropLast(it.length) }
        return stems.any { it in dict || "${it}e" in dict }
    }

    suspend fun extract(context: Context, uri: Uri): List<ExtractedWord> {
        val dict = dictionary(context)
        val image = InputImage.fromFilePath(context, uri)
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
        return VocabPairer { isWord(dict, it) }.pair(lines)
    }
}
