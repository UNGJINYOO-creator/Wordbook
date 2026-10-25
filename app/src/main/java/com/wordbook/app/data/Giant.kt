package com.wordbook.app.data

import android.content.Context

/** One of the 18 거대 물고기; each guards 100 of the built-in words. */
data class GiantFish(val emoji: String, val name: String, val english: String, val fact: String, val minKg: Double, val maxKg: Double)

/** Where the hunt stands: the current giant's block, how much health it has left, and what today's words would take off. */
data class GiantState(
    val index: Int,
    val words: List<Word>,
    val hit: Int,
    val pending: Int,
    val caught: Int,
    val extraLearned: Int,
) {
    val fish: GiantFish? get() = Giant.species.getOrNull(index)
    val total: Int get() = words.size
    val hp: Int get() = (total - hit).coerceAtLeast(0)
    val ready: Boolean get() = fish != null && total > 0 && hp == 0
    val done: Boolean get() = fish == null
    /** 열대 바다 fish opened by 100 learned words of the child's own decks. */
    val tropical: Int get() = extraLearned / Giant.BLOCK
}

/**
 * 거대 물고기 사냥: the built-in words in DAY order make 18 blocks of 100, one giant fish each.
 * A word hits the giant once it is learned and that day's 낚시 is won (or it was learned before the hunt began);
 * at 0 health the final battle with the block's words opens.
 */
class Giant(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("giant", Context.MODE_PRIVATE)

    val confirmed: Set<Long> get() = prefs.getStringSet("confirmed", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun confirm(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet("confirmed", (confirmed + ids).map { it.toString() }.toSet()).apply()
    }

    fun caughtAt(i: Int): Long = prefs.getLong("caught_$i", 0L)
    fun kg(i: Int): Double = prefs.getFloat("kg_$i", 0f).toDouble()
    fun recordCatch(i: Int, kg: Double) {
        prefs.edit().putLong("caught_$i", System.currentTimeMillis()).putFloat("kg_$i", kg.toFloat()).apply()
    }

    /** Set after losing the final battle; the missed words are looked at before the next try. */
    var reviewNeeded: Boolean
        get() = prefs.getBoolean("review", false)
        set(v) = prefs.edit().putBoolean("review", v).apply()

    fun state(all: List<Word>, learnedToday: Set<Long>): GiantState {
        // Words studied before the hunt existed count from the start.
        if (!prefs.getBoolean("seeded", false)) {
            confirm(all.filter { it.box > 0 || it.mastered }.map { it.id })
            prefs.edit().putBoolean("seeded", true).apply()
        }
        val order = DefaultDecks.wordOrder(context).withIndex().associate { (i, t) -> t to i }
        val builtIn = all.filter { it.addedAt == 0L && it.text.trim().lowercase() in order }
            .distinctBy { it.text.trim().lowercase() }
            .sortedBy { order[it.text.trim().lowercase()] }
        val blocks = builtIn.chunked(BLOCK)
        val index = (0 until species.size).firstOrNull { caughtAt(it) == 0L } ?: species.size
        val words = blocks.getOrElse(index) { emptyList() }
        val done = confirmed
        val builtInIds = builtIn.map { it.id }.toSet()
        return GiantState(
            index = index,
            words = words,
            hit = words.count { it.id in done },
            pending = words.count { it.id in learnedToday && it.id !in done },
            caught = (0 until species.size).count { caughtAt(it) > 0 },
            extraLearned = all.count { it.id !in builtInIds && it.id in done },
        )
    }

    companion object {
        const val BLOCK = 100
        /** The final battle starts this far out (the line snaps at the usual distance). */
        const val FINAL_START = 120
        const val CATCH_GIFT = 50

        val species = listOf(
            GiantFish("🐡", "거대 복어", "Giant Pufferfish", "위험하면 몸을 공처럼 빵빵하게 부풀려요.", 8.0, 20.0),
            GiantFish("🦑", "대왕 오징어", "Giant Squid", "눈이 축구공만 해요. 동물 중에서 가장 큰 눈이에요.", 150.0, 280.0),
            GiantFish("🐙", "대왕 문어", "Giant Pacific Octopus", "심장이 3개이고 피가 파란색이에요.", 30.0, 70.0),
            GiantFish("🦀", "왕게", "King Crab", "다리를 쭉 펴면 1.8m나 돼요.", 6.0, 12.0),
            GiantFish("🐢", "장수거북", "Leatherback Turtle", "등딱지가 딱딱하지 않고 가죽 같아요.", 300.0, 700.0),
            GiantFish("🐟", "황새치", "Swordfish", "칼 같은 주둥이를 휘둘러 먹이를 잡아요.", 200.0, 600.0),
            GiantFish("🐟", "개복치", "Ocean Sunfish", "알을 한 번에 3억 개나 낳아요.", 500.0, 2300.0),
            GiantFish("🦈", "귀상어", "Hammerhead Shark", "망치 같은 머리로 모래 속 먹이를 찾아요.", 200.0, 450.0),
            GiantFish("🐟", "참다랑어", "Bluefin Tuna", "바다를 가로질러 수천 km를 헤엄쳐요.", 250.0, 680.0),
            GiantFish("🐬", "범고래", "Orca", "사실은 고래가 아니라 가장 큰 돌고래예요.", 3600.0, 6000.0),
            GiantFish("🦭", "바다코끼리", "Walrus", "긴 엄니를 얼음에 꽂고 몸을 끌어올려요.", 800.0, 1700.0),
            GiantFish("🐟", "실러캔스", "Coelacanth", "공룡 시대부터 살아 온 '살아 있는 화석'이에요.", 60.0, 90.0),
            GiantFish("🦈", "백상아리", "Great White Shark", "피 한 방울 냄새도 멀리서 알아채요.", 700.0, 2000.0),
            GiantFish("🐋", "일각고래", "Narwhal", "머리의 긴 뿔은 사실 이빨이에요.", 800.0, 1600.0),
            GiantFish("🐟", "산갈치", "Giant Oarfish", "8m가 넘는, 뼈 있는 물고기 중 가장 긴 물고기예요.", 150.0, 270.0),
            GiantFish("🦈", "고래상어", "Whale Shark", "가장 큰 물고기인데 작은 플랑크톤만 먹어요.", 9000.0, 20000.0),
            GiantFish("🐳", "향유고래", "Sperm Whale", "대왕 오징어를 잡으러 2000m 아래까지 잠수해요.", 35000.0, 57000.0),
            GiantFish("🐋", "흰수염고래", "Blue Whale", "지구에서 가장 큰 동물이에요. 심장이 자동차만 해요.", 100000.0, 190000.0),
        )

        /** 열대 바다: one more for every 100 learned words of the child's own decks, round and round. */
        val tropical = listOf("🐠 흰동가리", "🐡 가시복", "🐠 나비고기", "🦐 공작갯가재", "🐙 파란고리문어", "🦞 닭새우", "🐠 쏠배감펭", "🐠 블루탱")
    }
}
