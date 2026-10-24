package com.wordbook.app.data

import android.content.Context
import java.util.Calendar

/** Builds the detailed daily Telegram report from today's answers. */
object Report {
    private val posNames = mapOf(
        "명" to "명사", "동" to "동사", "형" to "형용사", "부" to "부사",
        "대" to "대명사", "전" to "전치사", "접" to "접속사", "조" to "조동사",
    )
    private val modeNames = mapOf(StudyMode.CHOICE to "객관식", StudyMode.SPELLING to "빈칸", StudyMode.DICTATION to "듣고 쓰기")

    private fun pct(right: Int, n: Int) = if (n == 0) 0 else right * 100 / n

    private fun minutes(ms: Long) = "${ms / 60_000}분"

    suspend fun build(context: Context): String {
        val settings = StudySettings(context)
        val stats = DailyStats(context)
        val log = AnswerLog(context)
        val dao = AppDatabase.get(context).wordDao()
        val today = log.read()
        val yesterday = log.read(1)
        val goalMinutes = settings.dailyGoalMinutes
        val goalWords = settings.dailyWords

        return buildString {
            append("🎣 낚아보카 오늘의 학습 리포트\n\n")
            append("⏱ 학습 시간 ${minutes(stats.studyMs)}")
            if (goalMinutes > 0) append(" (목표 ${goalMinutes}분)")
            append("\n🧠 외운 단어 ${stats.learnedCount}개")
            if (goalWords > 0) append(" (목표 ${goalWords}개)")
            append("\n")
            if (today.isEmpty()) {
                append("\n오늘은 객관식·빈칸·듣고 쓰기 문제를 아직 안 풀었어요.\n")
                appendTomorrow(dao)
                if (isSunday()) appendWeek(log, stats)
                return@buildString
            }
            val right = today.count { it.correct }
            append("🎯 정답률 ${pct(right, today.size)}% (${today.size}문제 중 ${right}개)\n")
            val guesses = today.count { it.mode == StudyMode.CHOICE && it.ms in 1 until 1000 }
            if (guesses > 0) append("🎲 1초 안에 찍은 객관식 ${guesses}개 (물고기·목표에서 뺐어요)\n")

            // 1. First try vs. missed again and again.
            val byWord = today.groupBy { it.wordId }
            val firstTry = byWord.values.count { it.first().correct }
            val missedMany = byWord.values.filter { list -> list.count { !it.correct } >= 2 }
                .sortedByDescending { list -> list.count { !it.correct } }
            append("\n✅ 한 번에 맞힌 단어 ${firstTry}개 / ${byWord.size}개\n")
            if (missedMany.isNotEmpty()) {
                append("❗ 2번 이상 틀린 단어 ${missedMany.size}개: ")
                append(missedMany.take(10).joinToString(", ") { list -> "${list.first().text}(${list.count { !it.correct }})" })
                append("\n")
            }

            // 2. By part of speech.
            val byPos = today.filter { it.pos in posNames }.groupBy { it.pos }
                .map { (pos, list) -> Triple(posNames.getValue(pos), pct(list.count { it.correct }, list.size), list.size) }
                .sortedByDescending { it.third }
            if (byPos.isNotEmpty()) {
                append("\n📖 품사별: ")
                append(byPos.joinToString(" · ") { "${it.first} ${it.second}%" })
                append("\n")
                weakest(byPos)?.let { append("⚠️ ${it}가 약해요\n") }
            }

            // 3. By way of studying.
            val byMode = today.groupBy { it.mode }.filterKeys { it in modeNames }
                .map { (mode, list) -> Triple(modeNames.getValue(mode), pct(list.count { it.correct }, list.size), list.size) }
            if (byMode.isNotEmpty()) {
                append("✍️ 방식별: ")
                append(byMode.joinToString(" · ") { "${it.first} ${it.second}%" })
                append("\n")
                weakest(byMode)?.let { name ->
                    append(if (name == "객관식") "⚠️ 뜻 고르기가 약해요\n" else "⚠️ 스펠링($name)이 약해요\n")
                }
            }

            // 4. New words vs. review: remembered = the last answer today was right.
            val newWords = byWord.values.filter { it.first().isNew }
            val reviewWords = byWord.values.filter { !it.first().isNew }
            append("\n🆕 새 단어 ${newWords.size}개 중 ${newWords.count { it.last().correct }}개 익힘")
            append(" · 🔁 복습 ${reviewWords.size}개 중 ${reviewWords.count { it.first().correct }}개 기억\n")

            // 5. Confused pairs from wrong picks in 객관식, and typos in spelling.
            val pairs = today.filter { !it.correct && it.mode == StudyMode.CHOICE && it.response.isNotBlank() }
                .groupingBy { "${it.text} ↔ ${it.response}" }.eachCount().entries.sortedByDescending { it.value }
            if (pairs.isNotEmpty()) {
                append("🔀 헷갈린 짝: ${pairs.take(3).joinToString(", ") { it.key }}\n")
            }
            val typos = today.filter { !it.correct && it.mode != StudyMode.CHOICE && it.response.isNotBlank() }
                .distinctBy { it.wordId }
            if (typos.isNotEmpty()) {
                append("🔤 틀리게 쓴 스펠링: ${typos.take(5).joinToString(", ") { "${it.response}→${it.text}" }}\n")
            }

            // 6. Compared with yesterday and the last week.
            append("\n📈 ")
            if (yesterday.isNotEmpty()) {
                val diff = pct(right, today.size) - pct(yesterday.count { it.correct }, yesterday.size)
                append("정답률 어제보다 ${if (diff >= 0) "+" else ""}${diff}%")
            } else {
                append("어제는 문제를 안 풀었어요")
            }
            val past = stats.lastDays(8).dropLast(1).map { it.second }.filter { it > 0 }
            if (past.isNotEmpty()) {
                val diff = (stats.studyMs - past.average().toLong()) / 60_000
                append(" · 학습 시간 최근 평균보다 ${if (diff >= 0) "${diff}분 많음" else "${-diff}분 적음"}")
            }
            append("\n")

            // 7. Focus: answer speed in the first and second half of the day's questions.
            val times = today.map { it.ms }.filter { it in 500..120_000 }
            if (times.size >= 10) {
                val half = times.size / 2
                val first = times.take(half).average()
                val second = times.drop(half).average()
                append("⏳ 문제당 평균 ${"%.1f".format(times.average() / 1000)}초")
                when {
                    second > first * 1.3 -> append(" · 후반에 속도가 떨어졌어요(지친 것 같아요)")
                    second < first * 0.8 -> append(" · 갈수록 빨라졌어요 👍")
                    else -> append(" · 끝까지 집중했어요")
                }
                append("\n")
            }

            // 8. Tomorrow, and the week on Sundays.
            val weak = Weakness.lastWeek(log)
            val focus = listOfNotNull(
                weak.pos?.let { "${posNames[it] ?: it} 단어" },
                weak.mode?.let { modeNames[it]?.let { name -> "$name 문제" } },
            )
            if (focus.isNotEmpty()) append("🎯 약점 맞춤: 내일은 ${focus.joinToString("와 ")}를 더 많이 내요\n")
            appendTomorrow(dao)
            if (isSunday()) appendWeek(log, stats)
        }
    }

    /** The lowest accuracy, when it is clearly low and based on enough answers. */
    private fun weakest(rows: List<Triple<String, Int, Int>>): String? {
        val w = rows.filter { it.third >= 5 }.minByOrNull { it.second } ?: return null
        return if (w.second < 75 && rows.size > 1) w.first else null
    }

    private suspend fun StringBuilder.appendTomorrow(dao: WordDao) {
        val endOfTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
        append("📅 내일 복습할 단어 ${dao.countDue(endOfTomorrow)}개\n")
    }

    private fun isSunday() = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

    private fun StringBuilder.appendWeek(log: AnswerLog, stats: DailyStats) {
        val week = (0 until 7).flatMap { log.read(it) }
        val ms = stats.lastDays(7).sumOf { it.second }
        val days = stats.lastDays(7).count { it.second > 0 }
        append("\n🗓 이번 주 요약\n")
        append("공부한 날 ${days}일 · 총 ${minutes(ms)} · 외운 단어 ${stats.wordsLastDays(7)}개")
        if (week.isNotEmpty()) append(" · 정답률 ${pct(week.count { it.correct }, week.size)}%")
        append("\n")
        val hard = week.filter { !it.correct }.groupingBy { it.text }.eachCount().entries.sortedByDescending { it.value }
        if (hard.isNotEmpty()) append("이번 주 가장 많이 틀린 단어: ${hard.take(5).joinToString(", ") { it.key }}\n")
    }
}
