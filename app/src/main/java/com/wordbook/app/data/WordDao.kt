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

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWord(id: Long): Word?

    @Query("SELECT * FROM words")
    suspend fun getAllWords(): List<Word>

    @Query("SELECT * FROM words")
    fun observeAllWords(): Flow<List<Word>>

    /** Words learned before whose review time has come. */
    @Query("SELECT * FROM words WHERE box > 0 AND dueAt <= :now AND excluded = 0")
    suspend fun getDueWords(now: Long): List<Word>

    @Query("SELECT COUNT(*) FROM words WHERE box > 0 AND dueAt <= :now AND excluded = 0")
    fun observeDueCount(now: Long): Flow<Int>

    @Delete
    suspend fun deleteWord(word: Word)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)
}
