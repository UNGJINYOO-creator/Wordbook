package com.wordbook.app.ml

/** Heuristics for deciding whether a meaning read from the photo can be trusted. */
object MeaningCheck {
    // "(= Net)" style cross-references are fine to keep in a meaning.
    private val englishRef = Regex("\\(\\s*=?\\s*[A-Za-z][A-Za-z .'-]*\\)")
    private val hangul = Regex("[\\uAC00-\\uD7A3]")
    private val allowedPunct = Regex("[\\s()\\[\\]~,;.·/=:'-]")

    /** True if the OCR'd meaning looks broken: stray Latin letters/digits or mostly non-Korean. */
    fun looksGarbled(meaning: String): Boolean {
        val m = englishRef.replace(meaning, "").trim()
        if (m.isEmpty()) return true
        if (m.any { it in 'A'..'Z' || it in 'a'..'z' || it.isDigit() }) return true
        val chars = allowedPunct.replace(m, "")
        if (chars.isEmpty()) return true
        return hangul.findAll(chars).count() < chars.length * 0.8
    }

    /** True if the two Korean meanings share no syllable at all (likely different meanings). */
    fun disagree(a: String, b: String): Boolean {
        val sa = hangul.findAll(a).map { it.value }.toSet()
        val sb = hangul.findAll(b).map { it.value }.toSet()
        return sa.isNotEmpty() && sb.isNotEmpty() && sa.intersect(sb).isEmpty()
    }
}
