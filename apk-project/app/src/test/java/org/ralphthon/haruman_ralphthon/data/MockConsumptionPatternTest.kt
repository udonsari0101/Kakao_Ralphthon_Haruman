package org.ralphthon.haruman_ralphthon.data

import org.junit.Assert.*
import org.junit.Test

class MockConsumptionPatternTest {
    @Test fun eachTimePointDescribesTheSameCompleteHypotheticalCohort() {
        val points = MockConsumptionPatterns.points
        assertEquals(listOf(0, 1, 6, 24, 72, 168), points.map { it.elapsedHours })
        assertEquals(points.size, points.map { it.label }.distinct().size)
        points.forEach { point ->
            assertEquals(100, point.percentages.sum())
            assertTrue(point.percentages.all { it in 0..100 })
        }
        assertEquals(listOf(0, 100, 0), points.first().percentages)
        // This example follows one cohort without undo: final decisions accumulate, pending declines.
        points.zipWithNext().forEach { (before, after) ->
            assertTrue(after.buyPercent >= before.buyPercent)
            assertTrue(after.skipPercent >= before.skipPercent)
            assertTrue(after.pendingPercent <= before.pendingPercent)
        }
    }

    @Test fun barsCoverOneHundredPercentWithoutGapsOrNegativeWidthsEvenAtZeroShares() {
        MockConsumptionPatterns.points.forEach { point ->
            val bars = mockConsumptionSegments(point)
            assertEquals(0f, bars.first().startFraction, 0f)
            assertEquals(1f, bars.last().endFraction, 0f)
            bars.zipWithNext().forEach { (left, right) -> assertEquals(left.endFraction, right.startFraction, 0f) }
            bars.forEachIndexed { index, bar ->
                assertTrue(bar.startFraction in 0f..1f && bar.endFraction in bar.startFraction..1f)
                assertEquals(point.percentages[index] / 100f, bar.endFraction - bar.startFraction, .00001f)
            }
        }
        assertEquals(MockConsumptionSegment(0f, 0f), mockConsumptionSegments(MockConsumptionPatterns.points.first()).first())
    }

    @Test fun invalidSharesCannotBecomeMisleadingChartFixtures() {
        listOf(
            { MockConsumptionPoint(1, "1시간 뒤", 20, 40, 39) },
            { MockConsumptionPoint(1, "1시간 뒤", -1, 100, 1) },
            { MockConsumptionPoint(-1, "이전", 0, 100, 0) },
        ).forEach { invalid ->
            assertTrue(runCatching { invalid() }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
