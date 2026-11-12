package com.wordbook.app.ml

/**
 * Puts the AI's headwords together with what the on-device reader saw: the words and meanings come from
 * the AI, and the places in the photo (for the word strips) from the on-device text lines.
 */
object AiMerge {
    private val tokenRegex = Regex("[A-Za-z]+(?:['-][A-Za-z]+)*")

    fun merge(ai: List<AiWord>, ocr: PairResult, lines: List<OcrLine>): List<ExtractedWord> {
        val paired = ocr.words.associateBy { it.text.lowercase() }
        return ai.map { w ->
            val p = paired[w.word]
            ExtractedWord(
                text = w.word,
                meaning = w.meaning,
                pos = w.pos,
                wordBox = p?.wordBox ?: findBox(w.word, lines),
                meaningBox = p?.meaningBox,
            )
        }
    }

    /** The line where the word is printed: one whose English starts with it, else any line that has it. */
    fun findBox(word: String, lines: List<OcrLine>): Box? {
        val parts = word.lowercase().split(' ')
        val withTokens = lines.map { it to tokenRegex.findAll(it.text).map { t -> t.value.lowercase() }.toList() }
        fun has(tokens: List<String>) = tokens.windowed(parts.size).any { it == parts }
        val first = withTokens.filter { (_, t) -> t.take(parts.size) == parts }
            .ifEmpty { withTokens.filter { (_, t) -> has(t) } }
        // Several matches (the word is also in an example): the biggest print is the headword.
        return first.maxByOrNull { it.first.height }?.first?.box
    }
}
