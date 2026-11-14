package com.wordbook.app.data

/** How the 열대 바다 fish split the 학원 words. */
enum class TropicalMode(val label: String, val description: String) {
    BATCH("추가한 묶음마다", "한 번에 넣은 단어(예: 심슨 50개)마다 물고기 한 마리"),
    EVERY_30("30단어씩", "추가한 순서대로 30개씩 끊어서 한 마리"),
    DECK("단어장마다", "단어장 하나에 물고기 한 마리 (단어가 늘면 칸도 늘어요)"),
}

/**
 * 학원 단어 우선: the words of the decks the family adds (심슨단어장, 학교 시험대비 …) are the main study words.
 * The built-in 1800 words (the deck holding the words with addedAt = 0) and decks switched off in the settings
 * only fill in after them.
 */
class Priority(words: List<Word>, decks: List<Deck>, offNames: Set<String>) {
    /** The built-in 중학 영단어 deck: the one holding the most addedAt = 0 words. */
    private val builtInDeck: Long? = words.filter { it.addedAt == 0L }.groupingBy { it.deckId }.eachCount().maxByOrNull { it.value }?.key
    private val off: Set<Long> = decks.filter { it.name in offNames }.map { it.id }.toSet()

    /** A word of an added deck (switched on or off). */
    fun added(w: Word): Boolean = w.addedAt != 0L || (builtInDeck != null && w.deckId != builtInDeck)

    /** A 학원 word: an added deck that is switched on. */
    fun first(w: Word): Boolean = added(w) && w.deckId !in off

    companion object {
        /**
         * The new words of the day's 오늘 학습: every new 학원 word (at most [max] when it is above 0), then, if [fill],
         * other new words up to [limit] in all. [fresh] is newest first. In each part the weak part of speech comes
         * first for up to half of it.
         */
        fun todayNew(fresh: List<Word>, first: (Word) -> Boolean, limit: Int, max: Int, fill: Boolean, weakPos: String?): List<Word> {
            val (mine, rest) = fresh.partition(first)
            val minePick = weakFirst(mine, weakPos, if (max > 0) max else mine.size)
            val room = (limit - minePick.size).coerceAtLeast(0)
            val restPick = if (fill) weakFirst(rest, weakPos, room) else emptyList()
            return minePick + restPick
        }

        private fun weakFirst(words: List<Word>, pos: String?, n: Int): List<Word> =
            if (pos == null) words.take(n)
            else (words.filter { it.pos == pos }.take(n / 2) + words).distinctBy { it.id }.take(n)

        /**
         * Which group the next 낚시 question comes from: weights for each group, groups with no words skipped.
         * Returns null when no group with words has any weight (then every word is equally likely).
         */
        fun pickGroup(sizes: List<Int>, weights: List<Int>, roll: Double): Int? {
            val w = sizes.indices.map { if (sizes[it] > 0) weights.getOrElse(it) { 0 }.coerceAtLeast(0) else 0 }
            val total = w.sum()
            if (total == 0) return null
            var r = roll * total
            for (i in w.indices) {
                r -= w[i]
                if (r < 0 && w[i] > 0) return i
            }
            return w.indexOfLast { it > 0 }
        }

        /** Words put in within this long of each other, in the same deck, are one 묶음. */
        const val BATCH_GAP = 30L * 60 * 1000
        const val CHUNK = 30

        /** The 열대 바다 groups of the added words, oldest first; each group is one fish. */
        fun tropicalGroups(words: List<Word>, mode: TropicalMode): List<List<Word>> {
            val sorted = words.sortedWith(compareBy({ it.addedAt }, { it.id }))
            return when (mode) {
                TropicalMode.EVERY_30 -> sorted.chunked(CHUNK)
                TropicalMode.DECK -> sorted.groupBy { it.deckId }.values.toList()
                TropicalMode.BATCH -> {
                    val out = mutableListOf<MutableList<Word>>()
                    sorted.groupBy { it.deckId }.values.forEach { deck ->
                        var cur = mutableListOf<Word>()
                        deck.forEach { w ->
                            if (cur.isNotEmpty() && w.addedAt - cur.last().addedAt > BATCH_GAP) { out += cur; cur = mutableListOf() }
                            cur += w
                        }
                        if (cur.isNotEmpty()) out += cur
                    }
                    out.sortedBy { it.first().addedAt }
                }
            }
        }
    }
}

/** One 열대 바다 fish: its words, which of them were answered right, and the deck and day they came from. */
data class TropicalFish(
    val index: Int,
    val emoji: String,
    val name: String,
    val title: String,
    val words: List<Word>,
    val rightIds: Set<Long>,
) {
    val right: Int get() = words.count { it.id in rightIds }
    val caught: Boolean get() = words.isNotEmpty() && right == words.size
}
