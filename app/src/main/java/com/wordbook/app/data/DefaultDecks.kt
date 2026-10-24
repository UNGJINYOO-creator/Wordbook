package com.wordbook.app.data

import android.content.Context

/** Built-in word lists added once, on first launch or after an update. */
object DefaultDecks {
    private const val KEY_MIDDLE = "seededMiddle"

    /** Adds the 중학 영단어 종합단어장 from assets/default_middle.tsv (word, part of speech, meaning). */
    suspend fun seed(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("defaults", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIDDLE, false)) return
        val rows = context.assets.open("default_middle.tsv").bufferedReader().readLines()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3 || parts[0].isBlank()) null else parts
            }
        val dao = AppDatabase.get(context).wordDao()
        val deckId = dao.insertDeck(Deck(name = "중학 영단어", emoji = "📚", isCollection = true))
        // addedAt = 0 so these count as "old" words and new photo decks get the 최근 추가 share.
        dao.insertWords(rows.map { Word(deckId = deckId, text = it[0], pos = it[1], meaning = it[2], addedAt = 0) })
        prefs.edit().putBoolean(KEY_MIDDLE, true).apply()
    }
}
