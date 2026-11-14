package com.wordbook.app.data

import android.content.Context

/** The world voyage of the 거대 물고기 hunt, from the sea near Korea to the far south. */
enum class Ocean(val emoji: String, val title: String) {
    PACIFIC("🌏", "태평양"),
    INDIAN("🌍", "인도양"),
    ATLANTIC("🌎", "대서양"),
    ARCTIC("🧊", "북극해"),
    DEEP("🌑", "심해"),
    SOUTHERN("🐧", "남극해"),
}

/** One of the 18 거대 물고기, living in [ocean]; each guards 100 of the built-in words. */
data class GiantFish(val ocean: Ocean, val emoji: String, val name: String, val english: String, val fact: String, val minKg: Double, val maxKg: Double)

/** Where the hunt stands: the current giant's block, how much health it has left, and what today's words would take off. */
data class GiantState(
    val index: Int,
    val words: List<Word>,
    val hit: Int,
    val pending: Int,
    val caught: Int,
    /** Words of the block already landed on the giant, and today's that land with the next catch. */
    val hitIds: Set<Long> = emptySet(),
    val pendingIds: Set<Long> = emptySet(),
    /** 학원 words already landed on this giant, and today's that land with the next catch (학원 단어도 작살로). */
    val extraHit: List<Word> = emptyList(),
    val extraPending: List<Word> = emptyList(),
    /** Today's 학원 words that don't fit this giant any more; they go to the next one. */
    val extraOverflow: List<Long> = emptyList(),
    /** The 열대 바다 fish, one per group of 학원 words. */
    val tropicalFish: List<TropicalFish> = emptyList(),
) {
    val fish: GiantFish? get() = Giant.species.getOrNull(index)
    val total: Int get() = words.size
    val hp: Int get() = (total - hit).coerceAtLeast(0)
    val ready: Boolean get() = fish != null && total > 0 && hp == 0
    val done: Boolean get() = fish == null
    /** 열대 바다 fish caught: every word of the group answered right. */
    val tropical: Int get() = tropicalFish.count { it.caught }
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

    /** 학원 words landed on giant [i]. */
    fun extra(i: Int): Set<Long> = prefs.getStringSet("extra_$i", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun credit(i: Int, ids: Collection<Long>) {
        if (ids.isEmpty() || i !in species.indices) return
        prefs.edit().putStringSet("extra_$i", (extra(i) + ids).map { it.toString() }.toSet()).apply()
    }

    /** Words answered right in a quiz at least once, for the 열대 바다 squares. */
    val tropicalRight: Set<Long> get() = prefs.getStringSet("tropicalRight", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun markRight(id: Long) {
        val now = prefs.getStringSet("tropicalRight", emptySet())!!
        if (id.toString() in now) return
        prefs.edit().putStringSet("tropicalRight", now + id.toString()).apply()
    }

    /** Today's catch: the block's words and the 학원 words land on the giant. */
    fun land(st: GiantState?, learnedToday: Set<Long>) {
        if (st != null) {
            credit(st.index, st.extraPending.map { it.id })
            credit(st.index + 1, st.extraOverflow)
        }
        confirm(learnedToday)
    }

    fun state(all: List<Word>, learnedToday: Set<Long>, decks: List<Deck> = emptyList()): GiantState {
        // Words studied before the hunt existed count from the start.
        if (!prefs.getBoolean("seeded", false)) {
            confirm(all.filter { it.box > 0 || it.mastered }.map { it.id })
            prefs.edit().putBoolean("seeded", true).apply()
        }
        val settings = StudySettings(context)
        val priority = Priority(all, decks, settings.priorityOff)
        // 열대 바다 squares: words already known before the squares existed count as right.
        if (!prefs.getBoolean("tropicalSeeded", false)) {
            val landed = confirmed
            val known = all.filter { priority.added(it) && (it.known || it.box >= 2 || it.id in landed) }.map { it.id.toString() }
            prefs.edit().putStringSet("tropicalRight", prefs.getStringSet("tropicalRight", emptySet())!! + known)
                .putBoolean("tropicalSeeded", true).apply()
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
        val byId = all.associateBy { it.id }
        val extraHit = extra(index).mapNotNull { byId[it] }.take(words.size)
        val blockHit = words.count { it.id in done }
        val hit = (blockHit + extraHit.size).coerceAtMost(words.size)
        val pendingIds = words.filter { it.id in learnedToday && it.id !in done }.map { it.id }.toSet()
        val credited = (species.indices).flatMap { extra(it) }.toSet()
        val todayAcademy = if (settings.giantAcademy && words.isNotEmpty()) {
            all.filter { it.id in learnedToday && it.id !in builtInIds && it.id !in credited && !it.excluded && priority.first(it) }
                .distinctBy { it.text.trim().lowercase() }
        } else emptyList()
        val room = (words.size - hit - pendingIds.size).coerceAtLeast(0)
        val extraPending = todayAcademy.take(room)
        val right = tropicalRight
        val groups = Priority.tropicalGroups(all.filter { priority.added(it) && !it.excluded }, settings.tropicalMode)
        val deckNames = decks.associate { it.id to it.name }
        val day = java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA)
        val tropicalFish = groups.mapIndexed { i, g ->
            val t = tropical[i % tropical.size]
            val deckName = g.map { it.deckId }.distinct().joinToString(", ") { deckNames[it] ?: "단어장" }
            val title = when (settings.tropicalMode) {
                TropicalMode.DECK -> deckName
                else -> "$deckName · ${day.format(java.util.Date(g.first().addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()))}"
            }
            TropicalFish(i, t.substringBefore(' '), t.substringAfter(' '), title, g, right)
        }
        return GiantState(
            index = index,
            words = words,
            hit = hit,
            pending = pendingIds.size + extraPending.size,
            caught = (0 until species.size).count { caughtAt(it) > 0 },
            hitIds = words.filter { it.id in done }.map { it.id }.toSet(),
            pendingIds = pendingIds,
            extraHit = extraHit,
            extraPending = extraPending,
            extraOverflow = todayAcademy.drop(room).map { it.id },
            tropicalFish = tropicalFish,
        )
    }

    companion object {
        const val BLOCK = 100

        /** "거대 복어가" / "왕게가" / "대왕 문어가": the subject particle that fits the last syllable. */
        fun subject(name: String): String {
            val c = name.lastOrNull() ?: return name
            val batchim = c in '가'..'힣' && (c - '가') % 28 != 0
            return name + if (batchim) "이" else "가"
        }

        /** The one-line story of the current hunt. */
        fun story(f: GiantFish): String =
            "${subject(f.name)} ${f.ocean.title}에서 영단어 100개를 지키고 있어요. 단어를 하나 외울 때마다 작살 한 방!"
        /** The final battle starts this far out (the line snaps at the usual distance). */
        const val FINAL_START = 120
        const val CATCH_GIFT = 50

        val species = listOf(
            GiantFish(Ocean.PACIFIC, "🐡", "거대 복어", "Giant Pufferfish", "위험하면 몸을 공처럼 빵빵하게 부풀려요.", 8.0, 20.0),
            GiantFish(Ocean.PACIFIC, "🐙", "대왕 문어", "Giant Pacific Octopus", "심장이 3개이고 피가 파란색이에요.", 30.0, 70.0),
            GiantFish(Ocean.PACIFIC, "🦀", "왕게", "King Crab", "다리를 쭉 펴면 1.8m나 돼요.", 6.0, 12.0),
            GiantFish(Ocean.PACIFIC, "🐟", "참다랑어", "Bluefin Tuna", "바다를 가로질러 수천 km를 헤엄쳐요.", 250.0, 680.0),
            GiantFish(Ocean.INDIAN, "🐢", "장수거북", "Leatherback Turtle", "등딱지가 딱딱하지 않고 가죽 같아요.", 300.0, 700.0),
            GiantFish(Ocean.INDIAN, "🐟", "실러캔스", "Coelacanth", "공룡 시대부터 살아 온 '살아 있는 화석'이에요.", 60.0, 90.0),
            GiantFish(Ocean.INDIAN, "🦈", "고래상어", "Whale Shark", "가장 큰 물고기인데 작은 플랑크톤만 먹어요.", 9000.0, 20000.0),
            GiantFish(Ocean.ATLANTIC, "🐟", "황새치", "Swordfish", "칼 같은 주둥이를 휘둘러 먹이를 잡아요.", 200.0, 600.0),
            GiantFish(Ocean.ATLANTIC, "🐟", "개복치", "Ocean Sunfish", "알을 한 번에 3억 개나 낳아요.", 500.0, 2300.0),
            GiantFish(Ocean.ATLANTIC, "🦈", "귀상어", "Hammerhead Shark", "망치 같은 머리로 모래 속 먹이를 찾아요.", 200.0, 450.0),
            GiantFish(Ocean.ATLANTIC, "🦈", "백상아리", "Great White Shark", "피 한 방울 냄새도 멀리서 알아채요.", 700.0, 2000.0),
            GiantFish(Ocean.ARCTIC, "🦭", "바다코끼리", "Walrus", "긴 엄니를 얼음에 꽂고 몸을 끌어올려요.", 800.0, 1700.0),
            GiantFish(Ocean.ARCTIC, "🐋", "일각고래", "Narwhal", "머리의 긴 뿔은 사실 이빨이에요.", 800.0, 1600.0),
            GiantFish(Ocean.DEEP, "🦑", "대왕 오징어", "Giant Squid", "눈이 축구공만 해요. 동물 중에서 가장 큰 눈이에요.", 150.0, 280.0),
            GiantFish(Ocean.DEEP, "🐟", "산갈치", "Giant Oarfish", "8m가 넘는, 뼈 있는 물고기 중 가장 긴 물고기예요.", 150.0, 270.0),
            GiantFish(Ocean.DEEP, "🐳", "향유고래", "Sperm Whale", "대왕 오징어를 잡으러 2000m 아래까지 잠수해요.", 35000.0, 57000.0),
            GiantFish(Ocean.SOUTHERN, "🐬", "범고래", "Orca", "사실은 고래가 아니라 가장 큰 돌고래예요.", 3600.0, 6000.0),
            GiantFish(Ocean.SOUTHERN, "🐋", "흰수염고래", "Blue Whale", "지구에서 가장 큰 동물이에요. 심장이 자동차만 해요.", 100000.0, 190000.0),
        )

        /** 열대 바다: one fish for each group of 학원 words, round and round. */
        val tropical = listOf("🐠 흰동가리", "🐡 가시복", "🐠 나비고기", "🦐 공작갯가재", "🐙 파란고리문어", "🦞 닭새우", "🐠 쏠배감펭", "🐠 블루탱")
    }
}
