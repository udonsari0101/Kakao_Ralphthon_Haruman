package org.ralphthon.haruman_ralphthon.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class YardDataTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun instant(value: String, zone: TimeZone = utc): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = zone; isLenient = false }.parse(value)!!.time
    private fun item(id: String, created: Long, decision: Decision = Decision.PENDING, decided: Long? = null, price: Long? = null) =
        WishItem(id, id, 999L, Platform.MANUAL, "", "default", created, created + 1000L,
            decision = decision, decidedAt = decided, decisionPriceWon = price)

    @Test fun weekStartsMondayAndCrossesTheYearWithoutWeekNumberAmbiguity() {
        val bounds = periodBounds(instant("2027-01-03 23:59"), YardPeriod.WEEK, utc)
        assertEquals(instant("2026-12-28 00:00"), bounds.startInclusive)
        assertEquals(instant("2027-01-04 00:00"), bounds.endExclusive)
        assertTrue(bounds.startInclusive in bounds)
        assertTrue(bounds.endExclusive - 1 in bounds)
        assertFalse(bounds.endExclusive in bounds)
    }

    @Test fun leapMonthEndsAtNextLocalMonthAndDecemberAdvancesTheYear() {
        assertEquals(YardBounds(instant("2024-02-01 00:00"), instant("2024-03-01 00:00")),
            periodBounds(instant("2024-02-29 17:00"), YardPeriod.MONTH, utc))
        assertEquals(instant("2027-01-01 00:00"), periodBounds(instant("2026-12-31 23:59"), YardPeriod.MONTH, utc).endExclusive)
    }

    @Test fun identicalInstantCanBelongToDifferentMonthsInDifferentTimeZones() {
        val seoul = TimeZone.getTimeZone("Asia/Seoul")
        val time = instant("2026-09-30 16:00")
        assertEquals(instant("2026-09-01 00:00"), periodOf(time, YardPeriod.MONTH, utc))
        assertEquals(instant("2026-10-01 00:00", seoul), periodOf(time, YardPeriod.MONTH, seoul))
    }

    @Test fun daylightSavingWeekUsesLocalMidnightsRatherThanFixedSevenDays() {
        val losAngeles = TimeZone.getTimeZone("America/Los_Angeles")
        val bounds = periodBounds(instant("2026-03-08 12:00", losAngeles), YardPeriod.WEEK, losAngeles)
        assertEquals(instant("2026-03-02 00:00", losAngeles), bounds.startInclusive)
        assertEquals(instant("2026-03-09 00:00", losAngeles), bounds.endExclusive)
        assertEquals(167L * HOUR_MILLIS, bounds.endExclusive - bounds.startInclusive)
    }

    @Test fun pagesAreChronologicalIncludeCurrentAndNeverIncludeFuturePages() {
        val now = instant("2026-02-12 14:00")
        assertEquals(listOf("2025-11-01 00:00", "2025-12-01 00:00", "2026-01-01 00:00", "2026-02-01 00:00").map { instant(it) },
            periodsBetween(instant("2025-11-30 23:59"), now, YardPeriod.MONTH, utc))
        assertEquals(listOf(periodOf(now, YardPeriod.WEEK, utc)), periodsBetween(instant("2027-01-01 00:00"), now, YardPeriod.WEEK, utc))
    }

    @Test fun createdPeriodAndDecisionBudgetHaveIndependentMembership() {
        val september = instant("2026-09-01 00:00")
        val october = instant("2026-10-01 00:00")
        val earlier = item("earlier", september - 1, Decision.BUY, september, 100L)
        val later = item("later", september, Decision.BUY, october, 200L)
        val noDecisionDate = item("undated", september + 1, Decision.BUY, null, 300L)
        val summary = yardSummary(listOf(earlier, later, noDecisionDate), september, YardPeriod.MONTH, october, utc)
        assertEquals(listOf("later", "undated"), summary.items.map { it.id })
        assertEquals(500L, summary.boughtWon)
        assertEquals(listOf("earlier"), summary.budgetItems.map { it.id })
        assertEquals(100L, summary.budgetBoughtWon)
    }

    @Test fun examplesStayVisibleButUnknownAndZeroAmountsRemainDistinctInRealTotals() {
        val time = instant("2026-09-10 12:00")
        val values = listOf(
            item("zero", time, Decision.SKIP, time, 0L),
            item("unknown", time, Decision.SKIP, time, null),
            item("sample-tree", time, Decision.SKIP, time, 99_999L),
            item("buy-zero", time, Decision.BUY, time, 0L),
            item("buy-unknown", time, Decision.BUY, time, null),
            item("sample-buy", time, Decision.BUY, time, 99_999L),
        )
        val summary = yardSummary(values, time, YardPeriod.MONTH, time, utc)
        assertEquals(2, summary.sampleCount); assertEquals(3, summary.treeCount); assertEquals(2, summary.realTreeCount)
        assertEquals(0L, summary.savedWon); assertEquals(1, summary.unknownSavedCount)
        assertEquals(0L, summary.budgetBoughtWon); assertEquals(1, summary.budgetUnknownCount)
        assertEquals(2, summary.budgetItems.size); assertEquals("zero", summary.largestTree?.id)
    }

    @Test fun sumsSaturateInsteadOfWrappingAndIgnoreNegativeLegacyAmounts() {
        val time = instant("2026-09-10 12:00")
        val values = listOf(
            item("skip-max", time, Decision.SKIP, time, Long.MAX_VALUE), item("skip-one", time, Decision.SKIP, time, 1L),
            item("buy-max", time, Decision.BUY, time, Long.MAX_VALUE), item("buy-one", time, Decision.BUY, time, 1L),
            item("invalid", time, Decision.BUY, time, -100L),
        )
        val summary = yardSummary(values, time, YardPeriod.MONTH, time, utc)
        assertEquals(Long.MAX_VALUE, summary.savedWon)
        assertEquals(Long.MAX_VALUE, summary.boughtWon); assertEquals(Long.MAX_VALUE, summary.budgetBoughtWon)
        assertEquals(1, summary.budgetUnknownCount)
    }

    @Test fun sproutsRequireReadyNowAndCheeksUseTheFiveSpecifiedBands() {
        val time = instant("2026-09-10 12:00")
        val waiting = item("waiting", time)
        assertEquals(YardItemState.MOUND, yardItemState(waiting, time + 999))
        assertEquals(YardItemState.SPROUT, yardItemState(waiting, time + 1000))
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 4), listOf(-1, 0, 1, 2, 3, 5, 6, 9, 10).map(::cheekStage))
        assertNotEquals(yardPeriodKey(YardPeriod.MONTH, time), yardPeriodKey(YardPeriod.WEEK, time))
    }
}
