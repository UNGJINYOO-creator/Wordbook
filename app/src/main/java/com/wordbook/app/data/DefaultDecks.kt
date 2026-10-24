package com.wordbook.app.data

import android.content.Context

/** Built-in word lists added once, on first launch or after an update. */
object DefaultDecks {
    private const val KEY_MIDDLE = "seededMiddle"

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
