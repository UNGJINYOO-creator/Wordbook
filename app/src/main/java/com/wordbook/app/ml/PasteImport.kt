package com.wordbook.app.ml

/**
 * 붙여넣기로 단어 추가: turns pasted text (a word list copied from a note, a web page, a spreadsheet or a chat)
 * into decks of words. Pure Kotlin so it can be unit tested.
 *
 * - One word per line: "connect 연결하다", "connect - 연결하다", "connect\t동\t연결하다", "1. connect: (동) 연결하다",
 *   "connect v. 연결하다 n. 연결", or the other way round "연결하다 connect". A line with only the English word gets
 *   its meaning from the 낚아보카 사전 or the translator later.
 * - A line without a word starts a new deck: "# 1과", "[DAY 3]", "Unit 2", "3일차", "동물 단어", "단어장2:".
 */
object PasteImport {
    data class PastedWord(val text: String, val meaning: String, val pos: String)
    data class PastedDeck(val name: String, val words: List<PastedWord>)

    private val hangul = Regex("[가-힣]")
    private val latin = Regex("[A-Za-z]")
    private val bullet = Regex("^(?:[-*•·▶▷►○●■□◆◇☆★✓✔]+\\s*|\\(?\\d{1,4}\\s*[.)\\]]\\s*|\\d{1,4}\\s+(?=[A-Za-z])|[①-⑳]\\s*)")
    private val unitHeader = Regex(
        "^(?:day|unit|lesson|chapter|part|week|step|test|set|lv|level)\\s*[-.#]?\\s*\\d+.*$|^\\d+\\s*(?:과|일차|일째|강|단원|주차|주|장|회|차시|세트)(?:\\s.*)?$",
        RegexOption.IGNORE_CASE,
    )

    /** Part of speech written in English after the word: "connect v. 연결하다". */
    private val englishPos = linkedMapOf(
        "adverb" to "부", "adv" to "부", "ad" to "부",
        "adjective" to "형", "adj" to "형", "a" to "형",
        "verb" to "동", "vt" to "동", "vi" to "동", "v" to "동",
        "noun" to "명", "n" to "명",
        "pron" to "대", "prep" to "전", "conj" to "접", "aux" to "조", "art" to "관",
    )
    private val koreanPos = linkedMapOf(
        "형용사" to "형", "부사" to "부", "동사" to "동", "명사" to "명", "대명사" to "대",
        "전치사" to "전", "접속사" to "접", "조동사" to "조", "관사" to "관",
    )
    private const val SHORT = "명동형부대전접조관"

    // A part-of-speech mark inside the meaning: (동) [동] 【동】 <동> ⓥ, 동사: , "동 " at a boundary, v. n. adj.
    private val markInMeaning = Regex(
        "(?:[(\\[【<〈]\\s*(형용사|부사|동사|명사|대명사|전치사|접속사|조동사|관사|[$SHORT])\\s*[)\\]】>〉]\\s*)" +
            "|(?:(?<=^|[\\s,;/])(형용사|부사|동사|명사|대명사|전치사|접속사|조동사)\\s*[:.)]?\\s+)" +
            "|(?:(?<=^|\\s)([명동형부])\\s*[:.)]?\\s+(?=[가-힣~(]))" +
            "|(?:(?<=^|[\\s,;/])(adverb|adjective|verb|noun|adv|adj|prep|conj|pron|vt|vi|ad|v|n|a)\\.\\s*)",
        RegexOption.IGNORE_CASE,
    )

    private fun posOf(mark: String): String {
        val m = mark.lowercase()
        return koreanPos[m] ?: englishPos[m] ?: if (m.length == 1 && m[0] in SHORT) m else ""
    }

    /** True when the line names a deck rather than a word. */
    fun isHeader(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        if (t.startsWith("#") || t.startsWith("==") || t.startsWith("--") && t.endsWith("--")) return true
        if (Regex("^[\\[【<《〈][^\\]】>》〉]*[\\]】>》〉]$").matches(t)) return true
        if (t.endsWith(":") || t.endsWith("：")) return true
        if (unitHeader.matches(t)) return true
        // Korean only, no English word: "동물 단어", "중2 1학기".
        return !latin.containsMatchIn(t) && hangul.containsMatchIn(t)
    }

    fun headerName(line: String): String =
        line.trim().trimStart('#', '=', '-', ' ').trimEnd('=', '-', ':', '：', ' ')
            .removeSurrounding("[", "]").removeSurrounding("【", "】").removeSurrounding("<", ">")
            .removeSurrounding("《", "》").removeSurrounding("〈", "〉").trim()

    /**
     * The English word and the meaning on one line, or null if there is no English word on it.
     * The meaning keeps its parts of speech: "연결하다 / 연결" with pos "동".
     */
    fun parseLine(raw: String): PastedWord? {
        var line = raw.replace(' ', ' ').trim()
        line = bullet.replace(line, "").trim()
        if (!latin.containsMatchIn(line)) return null

        var english: String
        var meaning: String
        var pos = ""
        val cells = line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        if (cells.size >= 2 && latin.containsMatchIn(cells[0]) && !hangul.containsMatchIn(cells[0])) {
            english = cells[0]
            val rest = cells.drop(1).toMutableList()
            // "connect\t동\t연결하다": a column that is only a part of speech.
            val posIdx = rest.indexOfFirst { it.length <= 9 && posOf(it.trim('.', '(', ')', '[', ']', ' ')).isNotEmpty() }
            if (posIdx >= 0 && rest.size >= 2) pos = posOf(rest.removeAt(posIdx).trim('.', '(', ')', '[', ']', ' '))
            meaning = rest.firstOrNull { hangul.containsMatchIn(it) } ?: ""
        } else {
            val firstKo = hangul.find(line)?.range?.first
            if (firstKo == null) {
                english = line
                meaning = ""
            } else if (!latin.containsMatchIn(line.substring(0, firstKo))) {
                // "연결하다 - connect": Korean first, the English word at the end.
                val tail = Regex("([A-Za-z][A-Za-z '’.\\-]*)\\s*$").find(line) ?: return null
                english = tail.groupValues[1]
                meaning = line.substring(0, tail.range.first)
            } else {
                var cut = firstKo
                // Keep "~", "(", "[" that open the meaning: "about ~에 대해", "word (동) 연결하다".
                while (cut > 0 && line[cut - 1] in "~([【<〈 ") cut--
                english = line.substring(0, cut)
                meaning = line.substring(cut)
            }
        }

        // An English part-of-speech mark at the end of the word: "connect v." / "connect (v)".
        english = english.trim().trimEnd('-', '–', '—', ':', '：', '=', '|', ',', '/', '\t', ' ')
        val tailPos = Regex(
            "\\s+(?:[(\\[]([a-z]+)\\.?[)\\]]|(adverb|adjective|verb|noun)|(adv|adj|prep|conj|pron|vt|vi|ad|v|n|a)\\.)$",
            RegexOption.IGNORE_CASE,
        )
        tailPos.find(english)?.let { m ->
            val p = posOf(m.groupValues.drop(1).first { it.isNotEmpty() })
            if (p.isNotEmpty()) {
                if (pos.isEmpty()) pos = p
                english = english.substring(0, m.range.first)
            }
        }
        english = english.trim().trimEnd('-', '–', '—', ':', '：', '=', '|', ',', '/', '.', ' ').trim('"', '\'', '“', '”').replace(Regex("\\s+"), " ")
        if (english.isEmpty() || !latin.containsMatchIn(english)) return null

        val (m, p) = splitMeaning(meaning)
        return PastedWord(english, m, pos.ifEmpty { p })
    }

    /**
     * Splits a meaning at its part-of-speech marks: "(동) 연결하다 (명) 연결" -> "연결하다 / 연결", "동".
     * Without marks the part of speech is told from the Korean ending.
     */
    fun splitMeaning(raw: String): Pair<String, String> {
        val m = raw.trim().trimStart('-', '–', '—', ':', '：', '=', '|', ',', '/').trim()
        if (m.isEmpty()) return "" to ""
        val marks = markInMeaning.findAll(m).toList()
        if (marks.isEmpty()) return tidy(m).let { it to posOfMeaning(it) }
        val parts = mutableListOf<Pair<String, String>>()
        val head = m.substring(0, marks.first().range.first).trim()
        if (head.isNotEmpty() && hangul.containsMatchIn(head)) parts += tidy(head).let { it to posOfMeaning(it) }
        marks.forEachIndexed { i, mk ->
            val end = if (i + 1 < marks.size) marks[i + 1].range.first else m.length
            val text = tidy(m.substring(mk.range.last + 1, end))
            val p = posOf(mk.groupValues.drop(1).first { it.isNotEmpty() })
            if (text.isEmpty()) return@forEachIndexed
            val same = parts.indexOfFirst { it.second == p }
            if (same >= 0) parts[same] = (parts[same].first + ", " + text) to p else parts += text to p
        }
        if (parts.isEmpty()) return "" to ""
        return parts.joinToString(" / ") { it.first } to parts.first().second
    }

    /** Part of speech told from the first meaning's Korean ending ("빠른" 형, "빨리" 부, "연결하다" 동). */
    fun posOfMeaning(m: String): String {
        val first = m.split(" / ").first().split(',', ';').first().replace(Regex("\\[[^\\]]*]|\\([^)]*\\)"), "").trim()
        return if (first.isEmpty() || !hangul.containsMatchIn(first)) "" else PosGuess.guess(first)
    }

    private fun tidy(s: String) = s.trim().trimEnd(',', ';', '/', '|', '.', '-', '–', '—', ':', '=', ' ').trim().replace(Regex("\\s+"), " ")

    /**
     * Reads the whole paste. Words before the first deck name go into [firstName]. Decks without words are left
     * out, and a word that comes twice in one deck keeps its first meaning (the second one's added if different).
     */
    fun parse(text: String, firstName: String = "붙여넣은 단어장"): List<PastedDeck> {
        val decks = mutableListOf<Pair<String, MutableList<PastedWord>>>()
        var current: MutableList<PastedWord>? = null
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (isHeader(line)) {
                val name = headerName(line).ifEmpty { "단어장 ${decks.size + 1}" }
                current = mutableListOf<PastedWord>().also { decks += name to it }
                return@forEach
            }
            val w = parseLine(line) ?: return@forEach
            val list = current ?: mutableListOf<PastedWord>().also { decks += firstName to it; current = it }
            val same = list.indexOfFirst { it.text.equals(w.text, ignoreCase = true) }
            if (same < 0) list += w
            else if (w.meaning.isNotEmpty() && w.meaning !in list[same].meaning) {
                val old = list[same]
                list[same] = old.copy(meaning = listOf(old.meaning, w.meaning).filter { it.isNotEmpty() }.joinToString(" / "), pos = old.pos.ifEmpty { w.pos })
            }
        }
        // Two blocks with the same name become one deck.
        val merged = linkedMapOf<String, MutableList<PastedWord>>()
        decks.filter { it.second.isNotEmpty() }.forEach { (name, words) ->
            val into = merged.getOrPut(name) { mutableListOf() }
            words.forEach { w -> if (into.none { it.text.equals(w.text, ignoreCase = true) }) into += w }
        }
        return merged.map { (name, words) -> PastedDeck(name, words) }
    }

    /** What the paste check found for one word, shown on its row. */
    enum class Note(val label: String) {
        OK(""),
        FROM_DICTIONARY("뜻이 없어서 사전 뜻을 넣었어요"),
        TRANSLATE("뜻이 없어서 저장할 때 자동 번역해요"),
        POS_FIXED("품사를 뜻에 맞게 고쳤어요"),
        SPELLING_FIXED("스펠링을 고쳤어요"),
        SPELLING("사전에 없는 단어예요. 스펠링을 확인해 주세요"),
        DIFFERENT("사전 뜻과 달라요"),
        BROKEN("뜻이 이상해요"),
    }

    /** A pasted word after the check: what will be saved, what was pasted, and the dictionary's offer. */
    data class Checked(
        val word: PastedWord,
        val original: PastedWord,
        val note: Note,
        /** The 낚아보카 사전 meaning offered when [note] is [Note.DIFFERENT]: meaning and pos. */
        val offer: PastedWord? = null,
    ) {
        val needsLook get() = note == Note.DIFFERENT || note == Note.SPELLING || note == Note.BROKEN
    }

    /**
     * Second check of every pasted word against the 낚아보카 사전 and the spelling list: empty meanings get the
     * dictionary's, a part of speech that doesn't match the meaning is fixed, a meaning that clearly differs from
     * the dictionary is kept but shown with the dictionary's next to it.
     */
    fun check(w: PastedWord, dict: Dictionary, isWord: (String) -> Boolean, fix: (String) -> String?): Checked {
        var text = w.text
        var note = Note.OK
        val known: (String) -> Boolean = { isWord(it) || dict.senses(it).isNotEmpty() }
        val tokens = text.lowercase().split(Regex("[\\s/~]+")).map { it.trim('(', ')', '.', ',', '!', '?', '\'') }.filter { it.any(Char::isLetter) }
        if (!known(text.lowercase()) && tokens.any { t -> !known(t) && t.split('-').any { !known(it) } }) {
            val fixed = tokens.map { t -> if (known(t)) t else fix(t) }
            if (fixed.all { it != null } && tokens.size == 1) {
                text = fixed.joinToString(" ") { it!! }
                note = Note.SPELLING_FIXED
            } else {
                note = Note.SPELLING
            }
        }
        val senses = dict.senses(text)
        val fullDict = senses.joinToString(" / ") { it.meaning }
        val meaning = w.meaning
        if (meaning.isEmpty()) {
            return if (senses.isNotEmpty()) {
                Checked(PastedWord(text, fullDict, senses.first().pos), w, Note.FROM_DICTIONARY)
            } else {
                Checked(PastedWord(text, "", w.pos.ifEmpty { MeaningCheck.posFromEnglish(text) }), w, if (note == Note.SPELLING) note else Note.TRANSLATE)
            }
        }
        if (DeckCheck.broken(meaning)) {
            val offer = if (senses.isNotEmpty()) PastedWord(text, fullDict, senses.first().pos) else null
            return Checked(PastedWord(text, meaning, w.pos), w, Note.BROKEN, offer)
        }
        if (senses.isNotEmpty()) {
            val parts = meaning.split(" / ")
            val match = senses.firstOrNull { s -> parts.any { MeaningCheck.similar(it, s.meaning) } }
            if (match == null) {
                val first = senses.firstOrNull { it.pos == w.pos } ?: senses.first()
                return Checked(PastedWord(text, meaning, w.pos), w, Note.DIFFERENT, PastedWord(text, first.meaning, first.pos))
            }
            val main = parts.first()
            // Labelled a verb but written as a noun ("connect 동 연결"): the dictionary's verb meaning.
            if (w.pos == "동" && match.pos == "동" && MeaningCheck.posFromMeaning(main) != "동") {
                return Checked(PastedWord(text, (listOf(match.meaning) + parts.drop(1)).joinToString(" / "), "동"), w, Note.POS_FIXED)
            }
            // The meaning is right but labelled as another part of speech.
            if (w.pos != match.pos && MeaningCheck.similar(main, match.meaning) && senses.none { it.pos == w.pos }) {
                return Checked(PastedWord(text, meaning, match.pos), w, Note.POS_FIXED)
            }
            return Checked(PastedWord(text, meaning, w.pos), w, note)
        }
        // Not in the dictionary: the part of speech follows the meaning's ending when the two clearly disagree.
        val fromMeaning = posOfMeaning(meaning)
        if (w.pos == "동" && fromMeaning != "동" || w.pos != "동" && fromMeaning == "동" && w.pos.isNotEmpty()) {
            return Checked(PastedWord(text, meaning, fromMeaning), w, if (note == Note.OK) Note.POS_FIXED else note)
        }
        return Checked(PastedWord(text, meaning, w.pos.ifEmpty { fromMeaning }), w, note)
    }
}
