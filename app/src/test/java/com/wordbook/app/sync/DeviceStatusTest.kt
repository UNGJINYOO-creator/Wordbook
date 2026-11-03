package com.wordbook.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStatusTest {
    private val now = System.currentTimeMillis()
    private fun card(id: String, build: Int = 140, settings: Map<String, String> = mapOf("dailyGoal" to "20"), known: Long = 10, syncedAt: Long = now) =
        DeviceStatus.Card(id, id, false, build, syncedAt, settings, mapOf("words" to 100L, "known" to known))

    @Test fun sameEverywhereIsAllMatched() {
        val cards = listOf(card("a"), card("b"), card("c"))
        assertEquals("3대 모두 일치 ✅", DeviceStatus.summary(cards))
        assertFalse(DeviceStatus.needsAttention(cards))
    }

    @Test fun olderBuildAndDifferentSettingNeedAttention() {
        val cards = listOf(card("a"), card("b", build = 138, settings = mapOf("dailyGoal" to "30")))
        val diffs = DeviceStatus.settingDiffs(cards)
        assertEquals(listOf("dailyGoal" to listOf("20", "30")), diffs)
        assertTrue(DeviceStatus.needsAttention(cards))
        assertEquals("⚠️ 업데이트 필요 1대 · 설정 1개 다름", DeviceStatus.summary(cards))
    }

    @Test fun progressDifferenceIsShownButNoDot() {
        val cards = listOf(card("a"), card("b", known = 12))
        assertEquals(setOf("known"), DeviceStatus.statDiffs(cards))
        assertFalse(DeviceStatus.needsAttention(cards))
        assertEquals("⚠️ 진도 1개 다름", DeviceStatus.summary(cards))
    }

    @Test fun missingSettingCountsAsDifferent() {
        val cards = listOf(card("a"), card("b", settings = emptyMap()))
        assertEquals(listOf("dailyGoal" to listOf("20", "(없음)")), DeviceStatus.settingDiffs(cards))
    }

    @Test fun hiddenAndLongGoneDevicesAreLeftOut() {
        val gone = card("old", build = 100, syncedAt = now - 40L * 86_400_000)
        val hidden = card("h", build = 90).copy(hidden = true)
        val cards = listOf(card("a"), card("b"), gone, hidden)
        assertEquals(listOf("a", "b"), DeviceStatus.active(cards, now).map { it.id })
        assertEquals("2대 모두 일치 ✅", DeviceStatus.summary(cards))
    }

    @Test fun aloneThereIsNothingToCompare() {
        assertEquals("아직 다른 기기 정보가 없어요", DeviceStatus.summary(listOf(card("a"))))
    }
}
