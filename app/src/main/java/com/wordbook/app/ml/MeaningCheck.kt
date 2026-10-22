package com.wordbook.app.ml

/** Heuristics for deciding whether a meaning read from the photo can be trusted. */
object MeaningCheck {
    /** Syllables a circled part-of-speech badge (명/동/형/부...) is often misread as. */
    private const val BADGE_CHARS = "명영몡멍동둥등형헝혐협부브전접대감관"
    private val leadingSymbols = Regex("^[^가-힣A-Za-z(\\[~]+")

    /**
     * Cleans the meanings found on one page. Vocabulary books put a circled badge (명, 동, 형...)
     * before each meaning; OCR reads it as a symbol ("@", "®") or as a syllable glued to the
     * meaning ("동발명하다", "영웹사이트"). When most meanings on the page start that way it's a
     * badge, so it's removed.
     */
    fun cleanPage(raw: List<String>): List<String> {
        val trimmed = raw.map { it.trim() }
        val hadSymbol = trimmed.map { leadingSymbols.containsMatchIn(it) }
        val stage1 = trimmed.map { leadingSymbols.replace(it, "").trim() }
        val nonEmpty = stage1.count { it.isNotEmpty() }
        val badgeLike = stage1.indices.count { i ->
            hadSymbol[i] || (stage1[i].length > 2 && stage1[i][0] in BADGE_CHARS)
        }
        val badgeMode = nonEmpty >= 2 && badgeLike >= nonEmpty * 0.4
        return stage1.mapIndexed { i, m ->
            var r = m
            if (r.length > 2 && r[0] in BADGE_CHARS && (r[1] == ' ' || (badgeMode && !hadSymbol[i]))) {
                r = r.substring(1).trim()
            }
            tidy(r)
        }
    }

    /** Strips trailing punctuation and a dangling one-syllable fragment ("즉석의 긴" -> "즉석의"). */
    private fun tidy(m: String): String {
        var r = m.trim().trimEnd('.', ',', ';', ':', '·', '-').trim()
        val parts = r.split(' ')
        if (parts.size >= 2 && parts.last().length == 1 && hangul.matches(parts.last())) {
            r = parts.dropLast(1).joinToString(" ").trimEnd(',', ';').trim()
        }
        return r
    }

    /** Broken in a way the page cleanup can't fix: unbalanced brackets or garbled text. */
    fun suspicious(meaning: String): Boolean {
        if (looksGarbled(meaning)) return true
        return meaning.count { it == '[' } != meaning.count { it == ']' } ||
            meaning.count { it == '(' } != meaning.count { it == ')' }
    }

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
