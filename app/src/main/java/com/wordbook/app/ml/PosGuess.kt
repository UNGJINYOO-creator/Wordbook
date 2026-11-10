package com.wordbook.app.ml

/** Part of speech of a Korean meaning, told from its ending and lists of words the ending would mislead on. */
object PosGuess {
    // Korean words whose ending alone would be misread: adverbs, ㄴ-ending adjectives and nouns ending like adjectives.
    private val ADVERBS = setOf(
        "가까이", "가까이에", "가장", "간단히", "갑자기", "같이", "거꾸로", "거의", "게다가", "결코", "계속", "고도로", "곧", "곧장", "공정하게", "그러나",
        "그러므로", "그렇게", "그렇지만", "그만큼", "그저", "깊게", "꼭", "꽤", "나중에", "낮게", "내내", "내어", "내일", "너무", "너무나", "늘", "늦게",
        "다시", "다행히", "단단히", "단지", "대개", "대단히", "대신에", "더", "덜", "도", "돌아서", "드물게", "따라서", "따로", "때때로", "떠나서", "떨어져",
        "또한", "똑같이", "똑바로", "마구", "마지막으로", "마침내", "맞은편에", "매우", "매일", "멀리", "모두", "몹시", "물론", "물속으로", "바로", "밖에",
        "반쯤", "배로", "벌써", "보통", "부분적으로", "불구하고", "불행히도", "빨리", "서로", "세게", "소리로", "쉽게", "실제로", "아마", "아마도", "아무데도",
        "아예", "아주", "아직", "아직도", "않으면", "앞에", "앞으로", "어디", "어디나", "어디든", "어딘가에", "어떻게", "어제", "어쨌든", "언제", "언제나",
        "언제든", "언젠가", "역시", "열심히", "영원히", "오늘", "오래", "오른쪽으로", "오직", "오히려", "완전히", "왜", "외국에", "외로이", "요즘에는", "우선",
        "위층으로", "이따금", "이미", "이제", "일단", "일반적으로", "일찍", "자주", "잘", "전에", "정말", "정말로", "정확히", "조차", "좋게도", "주로",
        "주위에", "즉시", "지금", "지나서", "지하에", "직접", "진심으로", "진짜", "처음에", "최근에", "충분히", "타고", "특별히", "특히", "틀리게", "한번이라도",
        "함께", "해외로", "행복하게", "혼자", "확실히", "활짝", "후에", "훌륭히", "훨씬", "흔히",
    )
    private val ADJECTIVES = setOf(
        "가득찬", "가파른", "갇힌", "거친", "게으른", "낀", "나쁜", "낯선", "눈부신", "느린", "다른", "다친", "둥근", "드문", "드신", "때문인", "뛰어난",
        "마른", "막힌", "먼", "멋진", "목마른", "못생긴", "미친", "바른", "바쁜", "배고픈", "병든", "비싼", "빈", "빠른", "빠진", "사실인", "살찐", "성난",
        "슬픈", "싫증난", "싼", "쓰라린", "쓴", "아픈", "어떤", "엄청난", "예쁜", "예정인", "이른", "익힌", "잘생긴", "잠든", "지나간", "지난", "질긴",
        "짠", "큰", "타고난", "틀린", "풀린", "화난", "활기찬", "힘든",
    )
    private val NOUNS = setOf(
        "권한", "민주주의", "바다", "예의", "운", "은", "정의", "주의", "항의", "행운", "호의",
    )

    /**
     * Guesses the part of speech of one meaning part from its Korean ending:
     * "~하다" is a verb, "~한, ~의, ~적인" an adjective, "~히, ~하게" and known adverbs an adverb, anything else a noun.
     */
    fun guess(part: String): String {
        val t = part.replace(Regex("\\(.*?\\)"), "").trim().trim('~', '.', ' ')
        val piece = t.split(',').last().trim().ifEmpty { part.trim('~', '.', ' ') }
        val last = (piece.split(' ').lastOrNull { it.isNotBlank() } ?: piece).trimStart('~')
        return when {
            last in NOUNS -> "명"
            piece.endsWith("다") -> "동"
            last in ADVERBS || (last.length >= 2 && listOf("히", "하게", "롭게", "럽게").any { last.endsWith(it) }) -> "부"
            last in ADJECTIVES || listOf("한", "된", "은", "는", "운", "의", "적인").any { last.endsWith(it) } -> "형"
            else -> "명"
        }
    }
}
