package com.wordbook.app.data

import android.content.Context

/** Built-in word lists added once, on first launch or after an update. */
object DefaultDecks {
    private const val KEY_MIDDLE = "seededMiddle"

    /** Meanings corrected in an update ("word|pos" to the old meaning), so installed copies get the fix. */
    private val OLD_MEANINGS = mapOf(
        "adapt|동" to "적응시키다, 개작하다",
        "alphabet|명" to "알파벳, 자음모음",
        "bowl|명" to "사발",
        "counselor|명" to "카운셀러, 상담원",
        "driver|명" to "운전하는 사람",
        "frighten|동" to "소스라쳐 놀라게 하다",
        "ice|명" to "얼음, (영국)아이스크림",
        "let|동" to "~을 시키다, ~하게 하다",
        "like|동" to "좋아하다, 바라다",
        "merry|형" to "명랑한",
        "narrator|명" to "나레이터, 이야기하는 사람",
        "now|부" to "지금 / 지금",
        "officer|명" to "장교, 공무원, 순경",
        "pal|명" to "친구, 동아리",
        "scene|명" to "장면, 무대, 장치",
        "statue|명" to "상, 조상",
        "steak|명" to "두껍게 썬 고기, 비프 스테이크",
        "terrible|형" to "무서운, 지독한",
        "wrong|형" to "나쁜, 틀린",
    )

    private fun rows(context: Context) = context.assets.open("default_middle.tsv").bufferedReader().readLines()
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3 || parts[0].isBlank()) null else parts
        }

    /**
     * Adds the 중학 영단어 종합단어장 from assets/default_middle.tsv (word, part of speech, meaning, example),
     * and fills in examples for words that have none yet.
     */
    suspend fun seed(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("defaults", Context.MODE_PRIVATE)
        val dao = AppDatabase.get(context).wordDao()
        if (!prefs.getBoolean(KEY_MIDDLE, false)) {
            val deckId = dao.insertDeck(Deck(name = "중학 영단어", emoji = "📚", isCollection = true))
            // addedAt = 0 so these count as "old" words and new photo decks get the 최근 추가 share.
            dao.insertWords(rows(context).map {
                Word(deckId = deckId, text = it[0], pos = it[1], meaning = it[2], example = it.getOrElse(3) { "" }, addedAt = 0)
            })
            prefs.edit().putBoolean(KEY_MIDDLE, true).apply()
        }
        run {
            // A word still holding an old, corrected meaning gets the new one; edited words are left alone.
            val current = rows(context).associate { "${it[0]}|${it[1]}" to it[2] }
            dao.getAllWords().forEach { w ->
                val key = "${w.text}|${w.pos}"
                if (OLD_MEANINGS[key] == w.meaning) current[key]?.let { dao.updateWord(w.copy(meaning = it)) }
            }
        }
        run {
            // Each start: any word with the same spelling gets the example, new photo words included.
            val withExample = rows(context).filter { it.size > 3 && it[3].isNotBlank() }
            // can, may, will have two senses: match the part of speech first, then the spelling.
            val byPos = withExample.associate { "${it[0].lowercase()}|${it[1]}" to it[3] }
            val byText = withExample.associate { it[0].lowercase() to it[3] }
            dao.getAllWords().filter { it.example.isBlank() }.forEach { w ->
                val key = w.text.trim().lowercase()
                (byPos["$key|${w.pos}"] ?: byText[key])?.let { dao.updateWord(w.copy(example = it)) }
            }
        }
    }
}
