package org.ralphthon.haruman_ralphthon.data

/** Fixed presentation fixtures. These are not observations, forecasts or records of app users. */
internal data class MockConsumptionPoint(
    val elapsedHours: Int,
    val label: String,
    val buyPercent: Int,
    val pendingPercent: Int,
    val skipPercent: Int,
) {
    init {
        require(elapsedHours >= 0 && label.isNotBlank())
        require(listOf(buyPercent, pendingPercent, skipPercent).all { it in 0..100 })
        require(buyPercent + pendingPercent + skipPercent == 100)
    }

    val percentages: List<Int> get() = listOf(buyPercent, pendingPercent, skipPercent)
}

/** Half-open, contiguous fractions of one 100% bar, in buy / pending / skip order. */
internal data class MockConsumptionSegment(val startFraction: Float, val endFraction: Float)

internal fun mockConsumptionSegments(point: MockConsumptionPoint): List<MockConsumptionSegment> {
    var total = 0
    return point.percentages.map { percent ->
        val start = total
        total += percent
        MockConsumptionSegment(start / 100f, total / 100f)
    }
}

internal object MockConsumptionPatterns {
    // The same hypothetical cohort of 100 people at each point; every person's state is exclusive.
    val points: List<MockConsumptionPoint> = listOf(
        MockConsumptionPoint(0, "담은 직후", 0, 100, 0),
        MockConsumptionPoint(1, "1시간 뒤", 7, 81, 12),
        MockConsumptionPoint(6, "6시간 뒤", 16, 59, 25),
        MockConsumptionPoint(24, "하루 뒤", 24, 41, 35),
        MockConsumptionPoint(72, "3일 뒤", 30, 24, 46),
        MockConsumptionPoint(168, "7일 뒤", 34, 12, 54),
    )
}
