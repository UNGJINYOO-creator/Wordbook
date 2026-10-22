package com.wordbook.app.ml

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min

/** An English word/phrase found in the photo, with the Korean meaning printed next to it (if any). */
data class ExtractedWord(val text: String, val meaning: String)

/**
 * Runs on-device OCR (Korean + Latin) on a photo and pairs English words with the Korean
 * meanings printed beside them, as on a typical vocabulary sheet:
 *  - "apple 사과" on one line
 *  - an English column with a Korean column to its right
 *  - the Korean meaning on the line right below the word
 * English text without a nearby meaning (e.g. sentences) yields single words with no meaning.
 */
object WordExtractor {
    private val wordRegex = Regex("[A-Za-z]+(?:['-][A-Za-z]+)*")
    private val hangulRegex = Regex("[\\uAC00-\\uD7A3]")
    // Part-of-speech markers that often sit between the word and its meaning: "apple n. 사과"
    private val posRegex = Regex("\\b(n|v|vt|vi|a|adj|ad|adv|prep|conj|pron|int)\\.", RegexOption.IGNORE_CASE)
    // Leading list numbering: "1.", "12)", "③"
    private val numberingRegex = Regex("^[\\s\\d.)\\]①-⑳-]+")
    // Pronunciation like "[əbǽndən]" or "/ˈæp.əl/"
    private val phoneticRegex = Regex("\\[[^\\]]*]|/[^/]*/")

    /** A short English entry (word or phrase) rather than a sentence. */
    private const val MAX_PHRASE_WORDS = 4

    private val stopWords = setOf(
        "a", "an", "the", "and", "or", "but", "if", "of", "to", "in", "on", "at", "by", "for",
        "with", "from", "as", "is", "am", "are", "was", "were", "be", "been", "it", "its",
        "this", "that", "these", "those", "i", "you", "he", "she", "we", "they", "me", "him",
        "her", "us", "them", "my", "your", "his", "our", "their", "do", "does", "did", "not",
        "no", "so", "up", "out", "can", "will", "has", "have", "had", "there", "what", "which",
        "who", "when", "where", "how", "all", "s", "t", "ll", "re", "ve", "d", "m",
    )

    private class Line(val text: String, val box: Rect?)

    suspend fun extract(context: Context, uri: Uri): List<ExtractedWord> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val lines = try {
            recognizer.process(image).await().textBlocks.flatMap { b -> b.lines.map { Line(it.text, it.boundingBox) } }
        } finally {
            recognizer.close()
        }
        return pair(lines)
    }

    private fun englishWords(s: String) = wordRegex.findAll(posRegex.replace(s, " ")).map { it.value }.toList()

    private fun cleanMeaning(s: String) = s.trim().trim(':', '-', '=', '·', ',').trim()

    private fun pair(lines: List<Line>): List<ExtractedWord> {
        val result = linkedMapOf<String, ExtractedWord>()
        fun add(text: String, meaning: String) {
            val key = text.lowercase()
            val existing = result[key]
            if (existing == null || (existing.meaning.isBlank() && meaning.isNotBlank())) {
                result[key] = ExtractedWord(key, meaning)
            }
        }

        val englishOnly = mutableListOf<Line>()
        val koreanOnly = mutableListOf<Line>()
        for (line in lines) {
            val text = phoneticRegex.replace(numberingRegex.replace(line.text, ""), " ")
            val hangul = hangulRegex.find(text)
            val english = englishWords(if (hangul != null) text.substring(0, hangul.range.first) else text)
            when {
                hangul == null && english.isNotEmpty() -> englishOnly += Line(text, line.box)
                hangul != null && english.isEmpty() -> koreanOnly += Line(cleanMeaning(text), line.box)
                // "apple 사과" / "give up 포기하다" on the same line
                hangul != null && english.size <= MAX_PHRASE_WORDS ->
                    add(english.joinToString(" "), cleanMeaning(text.substring(hangul.range.first)))
                // Long mixed line (e.g. an example sentence with a translation): just collect words.
                hangul != null -> englishOnly += Line(text.substring(0, hangul.range.first), line.box)
            }
        }

        val used = mutableSetOf<Line>()
        for (line in englishOnly) {
            val words = englishWords(line.text)
            val meaning = if (words.size <= MAX_PHRASE_WORDS) findMeaning(line, koreanOnly, used) else null
            if (meaning != null) {
                add(words.joinToString(" "), meaning.text)
            } else {
                words.map { it.lowercase() }
                    .filter { it.length >= 2 && it !in stopWords }
                    .forEach { add(it, "") }
            }
        }
        return result.values.toList()
    }

    /** Korean line to the right on the same row, else directly below and left-aligned. */
    private fun findMeaning(line: Line, korean: List<Line>, used: MutableSet<Line>): Line? {
        val e = line.box ?: return null
        val sameRow = korean.filter { it !in used && it.box != null }
            .filter { k ->
                val b = k.box!!
                val overlap = min(e.bottom, b.bottom) - max(e.top, b.top)
                overlap > 0.5f * min(e.height(), b.height()) && b.left >= e.right - e.height()
            }
            .minByOrNull { it.box!!.left - e.right }
        val below = sameRow ?: korean.filter { it !in used && it.box != null }
            .filter { k ->
                val b = k.box!!
                val gap = b.top - e.bottom
                gap >= -e.height() / 3 && gap < e.height() && kotlin.math.abs(b.left - e.left) < 2 * e.height()
            }
            .minByOrNull { it.box!!.top - e.bottom }
        return below?.also { used += it }
    }
}
