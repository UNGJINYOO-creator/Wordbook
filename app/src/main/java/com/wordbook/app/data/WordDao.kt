package com.wordbook.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query(
        """
        SELECT d.id, d.name, d.createdAt,
               COUNT(w.id) AS wordCount,
               COALESCE(SUM(CASE WHEN w.known THEN 1 ELSE 0 END), 0) AS knownCount
        FROM decks d LEFT JOIN words w ON w.deckId = d.id
        GROUP BY d.id ORDER BY d.createdAt DESC
        """
    )
    fun observeDecks(): Flow<List<DeckSummary>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeck(id: Long): Deck?

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeDeck(id: Long): Flow<Deck?>

    @Query("SELECT * FROM words WHERE deckId = :deckId ORDER BY text COLLATE NOCASE")
    fun observeWords(deckId: Long): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE deckId = :deckId")
    suspend fun getWords(deckId: Long): List<Word>

    @Query("SELECT DISTINCT LOWER(text) FROM words")
    suspend fun allWordTexts(): List<String>

    @Insert
    suspend fun insertDeck(deck: Deck): Long

    @Insert
    suspend fun insertWord(word: Word): Long

    @Insert
    suspend fun insertWords(words: List<Word>)

    @Update
    suspend fun updateWord(word: Word)

    @Update
    suspend fun updateDeck(deck: Deck)

    @Query("UPDATE words SET known = 1, correctCount = correctCount + 1 WHERE id = :id")
    suspend fun markKnown(id: Long)

    @Query("UPDATE words SET known = 1, mastered = 1, correctCount = correctCount + 1 WHERE id = :id")
    suspend fun markMastered(id: Long)

    @Query("UPDATE words SET known = 0, mastered = 0, wrongCount = wrongCount + 1 WHERE id = :id")
    suspend fun markUnknown(id: Long)

    @Delete
    suspend fun deleteWord(word: Word)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)
}
