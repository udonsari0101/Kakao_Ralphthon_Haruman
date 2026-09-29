package org.ralphthon.haruman_ralphthon.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class TerraceLayoutTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = instant("2026-09-29 12:00")
    private fun instant(value: String, zone: TimeZone = utc): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = zone; isLenient = false }.parse(value)!!.time
    private fun item(id: String, created: Long, decision: Decision = Decision.PENDING, snapshot: Long? = null) =
        WishItem(id, id, 999_999, Platform.MANUAL, "", "bag", created, now + 3_600_000,
            decision = decision, decidedAt = if (decision == Decision.PENDING) null else created, decisionPriceWon = snapshot)

    @Test fun septemberIncludesFiveFullMondayWeeksAndFebruaryCanHaveExactlyFour() {
        assertEquals(listOf("2026-08-31 00:00", "2026-09-07 00:00", "2026-09-14 00:00", "2026-09-21 00:00", "2026-09-28 00:00").map { instant(it) },
            weeksOfMonth(2026, 9, utc))
        assertEquals(4, weeksOfMonth(2021, 2, utc).size)
        assertEquals(6, weeksOfMonth(2020, 8, utc).size)
        assertEquals(listOf(instant("2026-09-28 00:00")), terraceWeeks(now, YardPeriod.WEEK, utc))
        assertEquals(weeksOfMonth(2026, 9, utc), terraceWeeks(now, YardPeriod.MONTH, utc))
    }

    @Test fun boundaryWeekIncludesAugustAndOctoberWhileMonthSummaryStaysInSeptember() {
        val august = item("august", instant("2026-08-31 10:00"), Decision.SKIP, 100)
        val september = item("september", instant("2026-09-01 10:00"), Decision.SKIP, 200)
        val october = item("october", instant("2026-10-01 10:00"), Decision.BUY, 300)
        val records = listOf(august, september, october)
        val month = yardSummary(records, now, YardPeriod.MONTH, now, utc)
        assertEquals(listOf("september"), month.realItems.map { it.id })
        assertEquals(200L, month.savedWon)
        val first = layoutWeek(records, instant("2026-09-01 00:00"), 292f, now, timeZone = utc)
        assertEquals(listOf("august", "september"), first.summary.realItems.map { it.id })
        assertEquals(300L, first.summary.savedWon)
        val last = layoutWeek(records, now, 292f, now, timeZone = utc)
        assertEquals(listOf("october"), last.summary.realItems.map { it.id })
        assertEquals(300L, last.summary.boughtWon)
    }

    @Test fun daylightSavingDaysAreLocalCalendarDaysAndYearBoundaryStartsOnMonday() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        val at = instant("2026-03-08 12:00", zone)
        val layout = layoutWeek(emptyList(), at, 292f, at, timeZone = zone)
        assertEquals(instant("2026-03-02 00:00", zone), layout.bounds.startInclusive)
        assertEquals(167 * 3_600_000L, layout.bounds.endExclusive - layout.bounds.startInclusive)
        assertEquals(7, layout.days.size)
        assertEquals(23 * 3_600_000L, layout.days.last().endExclusive - layout.days.last().dayStart)
        assertEquals(instant("2026-12-28 00:00"), weekStart(instant("2027-01-03 23:59"), utc))
    }

    @Test fun weightedSlotsUseCountsCappedAtThreeAndEmptyWeightPoint55() {
        val monday = instant("2026-09-07 00:00")
        val records = listOf(item("m", monday)) + (1..3).map { item("t$it", monday + 86_400_000 + it) } +
            (1..7).map { item("w$it", monday + 2 * 86_400_000 + it) }
        val layout = layoutWeek(records, monday, 300f, now, timeZone = utc)
        assertEquals(listOf(1f, 3f, 3f, .55f, .55f, .55f, .55f), layout.days.map { it.weight })
        assertEquals(300f / 9.2f, layout.days[0].widthPx, .001f)
        assertEquals(3f, layout.days[1].widthPx / layout.days[0].widthPx, .0001f)
        assertEquals(0f, layout.days.first().leftPx, 0f)
        assertEquals(300f, layout.days.last().leftPx + layout.days.last().widthPx, .001f)
        assertEquals(1, layout.overflows.single().count)
    }

    @Test fun frontThreeAndBackThreePreserveScaleLiftAndRightOffset() {
        val day = instant("2026-09-09 00:00")
        val records = (0..5).map { item("i$it", day + it) }
        val layout = layoutWeek(records, day, 600f, now, timeZone = utc)
        val front = layout.plants.filterNot { it.isBack }
        val back = layout.plants.filter { it.isBack }
        assertEquals(listOf("i0", "i1", "i2"), front.map { it.item.id })
        assertEquals(listOf("i3", "i4", "i5"), back.map { it.item.id })
        assertEquals(44f * .86f, front[0].sizePx, .0001f)
        assertEquals(front[0].sizePx * .78f, back[0].sizePx, .0001f)
        assertEquals(10f, front[0].bottomPx - back[0].bottomPx, .0001f)
        val slot = layout.days.first { it.dayStart == day }
        assertEquals(slot.widthPx * .12f, back[0].centerXPx - front[0].centerXPx, .001f)
        assertTrue(layout.plants.take(3).all { it.layer == TerraceLayer.BACK })
    }

    @Test fun createdTimeThenIdMakesCoordinatesStableAcrossInputOrderAndPriceEdits() {
        val day = instant("2026-09-09 00:00")
        val records = listOf(item("b", day, Decision.SKIP, 50_000), item("a", day, Decision.SKIP, 0), item("c", day + 1, Decision.BUY, 10_000))
        val first = layoutWeek(records, day, 292f, now, timeZone = utc)
        val second = layoutWeek(records.reversed().map { it.copy(priceWon = 50_000_000) }, day, 292f, now, timeZone = utc)
        assertEquals(listOf("a", "b", "c"), first.plants.map { it.item.id })
        assertEquals(first.plants.map { listOf(it.leftPx, it.topPx, it.sizePx) }, second.plants.map { listOf(it.leftPx, it.topPx, it.sizePx) })
        assertEquals(listOf(TerracePlantKind.TREE_SMALL, TerracePlantKind.TREE_BIG, TerracePlantKind.PIT), first.plants.map { it.kind })
        assertEquals(first.summary.savedWon, second.summary.savedWon)
    }

    @Test fun growthKeepsTheLogicalAnchorAndUnknownAndSamplesDoNotBecomeFinancialFacts() {
        val day = instant("2026-09-07 00:00")
        val waiting = item("real", day)
        val small = layoutWeek(listOf(waiting), day, 292f, now, timeZone = utc).plants.single()
        val grown = layoutWeek(listOf(waiting.copy(decision = Decision.SKIP, decidedAt = now, decisionPriceWon = 200_000)), day, 292f, now, timeZone = utc).plants.single()
        assertEquals(small.centerXPx, grown.centerXPx, .0001f)
        assertEquals(TerracePlantKind.TREE_FRUIT, grown.kind)
        val records = listOf(item("unknown", day, Decision.SKIP), item("zero", day, Decision.SKIP, 0), item("sample-hidden", day, Decision.SKIP, 1_000_000))
        val layout = layoutWeek(records, day, 292f, now, timeZone = utc)
        assertEquals(2, layout.plants.size)
        assertEquals(2, layout.summary.realTreeCount)
        assertEquals(1, layout.summary.unknownSavedCount)
        assertEquals(0L, layout.summary.savedWon)
        assertTrue(layout.plants.all { it.kind == TerracePlantKind.TREE_SMALL })
        assertFalse(layout.days.any { dayZone -> dayZone.items.any { it.isSample } })
    }

    @Test fun layoutNeverClipsAnyPlantEvenAtTinyWidthsOrHigherDensity() {
        val records = clusteredSeptemberFixture()
        for (width in listOf(0f, 1f, 32f, 120f, 232f, 292f, 412f)) for (base in listOf(44f, 66f, 132f)) {
            for (week in weeksOfMonth(2026, 9, utc)) {
                val layout = layoutWeek(records, week, width, now, base, utc)
                layout.plants.forEach { plant ->
                    assertTrue("left: $width/$base ${plant.item.id}", plant.leftPx >= -.001f)
                    assertTrue("right: $width/$base ${plant.item.id}", plant.leftPx + plant.sizePx <= width + .001f)
                    assertTrue("top: $width/$base ${plant.item.id}", plant.topPx >= -.001f)
                    assertTrue("bottom: $width/$base ${plant.item.id}", plant.bottomPx <= layout.heightPx + .001f)
                    assertTrue(plant.leftPx.isFinite() && plant.topPx.isFinite() && plant.sizePx.isFinite())
                }
            }
        }
    }

    @Test fun supplied37ItemClusterDistributionKeepsAllItemsAndShowsOnlyOneOverflow() {
        val records = clusteredSeptemberFixture()
        val layouts = weeksOfMonth(2026, 9, utc).map { layoutWeek(records, it, 292f, now, timeZone = utc) }
        assertEquals(37, records.size)
        assertEquals(37, layouts.sumOf { it.days.sumOf { day -> day.count } })
        assertEquals(36, layouts.sumOf { it.plants.size })
        val overflows = layouts.flatMap { it.overflows }
        assertEquals(1, overflows.size)
        assertEquals(instant("2026-09-13 00:00"), overflows.single().dayStart)
        assertEquals(1, overflows.single().count)
        assertEquals(6, layouts.flatMap { it.days }.single { it.dayStart == instant("2026-09-26 00:00") }.count)
        assertTrue(layouts.last().days.single { it.dayOfMonth == 30 }.isFuture)
        assertTrue(layouts.last().days.single { it.dayOfMonth == 29 }.isToday)
    }

    @Test fun readyBoundaryAndTreeSizeBandsUseCurrentReadinessAndDecisionSnapshot() {
        val created = instant("2026-09-29 00:00")
        val waiting = item("waiting", created).copy(cooldownUntil = now)
        assertEquals(TerracePlantKind.MOUND, terracePlantKind(waiting, now - 1))
        assertEquals(TerracePlantKind.SPROUT, terracePlantKind(waiting, now))
        assertEquals(listOf(TerracePlantKind.TREE_SMALL, TerracePlantKind.TREE_SMALL, TerracePlantKind.TREE_BIG, TerracePlantKind.TREE_BIG, TerracePlantKind.TREE_FRUIT),
            listOf(null, 49_999L, 50_000L, 199_999L, 200_000L).map { terracePlantKind(item("price", created, Decision.SKIP, it), now) })
    }

    @Test fun crowdedWeightedDaysRequestFlowWhenClampedDateLabelsWouldOverlap() {
        val monday = instant("2026-09-07 00:00")
        val records = (0..6).flatMap { day ->
            (0 until (if (day < 2) 1 else 3)).map { index -> item("crowded-$day-$index", monday + day * 86_400_000L + index) }
        }
        for (fontScale in listOf(1f, 1.3f)) {
            val plantWidth = 320f - 20f - 52f * fontScale - 10f - 4f
            val week = layoutWeek(records, monday, plantWidth, now, timeZone = utc)
            val labels = layoutTerraceDayLabels(week.days.map { it.centerXPx }, plantWidth, 40f)
            assertTrue("The original fixed labels overlap at fontScale=$fontScale", labels.requiresFlow)
            assertTrue(labels.leftPositionsPx[1] < labels.leftPositionsPx[0] + 40f)
        }
    }

    @Test fun separatedDateLabelsKeepPositionsAndTinyWidthsRequestFlow() {
        val labels = layoutTerraceDayLabels(listOf(10f, 100f, 190f), 200f, 40f)
        assertFalse(labels.requiresFlow)
        assertEquals(listOf(0f, 80f, 160f), labels.leftPositionsPx)
        assertTrue(layoutTerraceDayLabels(listOf(10f), 20f, 40f).requiresFlow)
        assertFalse(layoutTerraceDayLabels(emptyList(), 0f, 40f).requiresFlow)
    }

    /** Same 37-item day clusters as the supplied data.py; no Python execution or production seed. */
    private fun clusteredSeptemberFixture(): List<WishItem> {
        val plan = listOf(2 to 2, 4 to 1, 6 to 3, 9 to 1, 13 to 7, 14 to 3, 16 to 2, 19 to 2, 23 to 2, 26 to 6, 27 to 5, 29 to 2, 30 to 1)
        return plan.flatMap { (day, count) ->
            (0 until count).map { index ->
                val created = instant("2026-09-${day.toString().padStart(2, '0')} 08:00") + index * 60_000
                item("fixture-$day-$index", created, when (index % 3) { 0 -> Decision.SKIP; 1 -> Decision.BUY; else -> Decision.PENDING },
                    when (index % 4) { 0 -> null; 1 -> 40_000L; 2 -> 80_000L; else -> 200_000L }).copy(cooldownUntil = if (day < 29) now - 1 else now + 3_600_000)
            }
        }
    }
}
