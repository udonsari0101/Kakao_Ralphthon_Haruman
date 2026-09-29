package org.ralphthon.haruman_ralphthon.data

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Resource-independent art names. Decided tree sizes always use the immutable decision snapshot. */
enum class TerracePlantKind(val sizeFactor: Float) {
    MOUND(.86f), SPROUT(.98f), TREE_SMALL(.92f), TREE_BIG(1.08f), TREE_FRUIT(1.18f), PIT(1f),
}

enum class TerraceLayer { BACK, FRONT }

data class TerracePlant(
    val item: WishItem,
    val dayStart: Long,
    val kind: TerracePlantKind,
    val layer: TerraceLayer,
    val leftPx: Float,
    val topPx: Float,
    val sizePx: Float,
) {
    val centerXPx: Float get() = leftPx + sizePx / 2f
    val bottomPx: Float get() = topPx + sizePx
    val isBack: Boolean get() = layer == TerraceLayer.BACK
}

data class TerraceDayZone(
    val dayStart: Long,
    val endExclusive: Long,
    val dayIndex: Int,
    val dayOfMonth: Int,
    val leftPx: Float,
    val widthPx: Float,
    val weight: Float,
    val items: List<WishItem>,
    val isToday: Boolean,
    val isFuture: Boolean,
) {
    val centerXPx: Float get() = leftPx + widthPx / 2f
    val count: Int get() = items.size
    val showLabel: Boolean get() = items.isNotEmpty() || isToday
}

data class TerraceOverflow(val dayStart: Long, val count: Int, val centerXPx: Float)

data class TerraceLabelLayout(val leftPositionsPx: List<Float>, val requiresFlow: Boolean)

/** Label boxes must not overlap when unequal day weights put their centers close together. */
fun layoutTerraceDayLabels(centersPx: List<Float>, widthPx: Float, labelWidthPx: Float): TerraceLabelLayout {
    require(widthPx.isFinite() && widthPx >= 0f)
    require(labelWidthPx.isFinite() && labelWidthPx > 0f)
    require(centersPx.all { it.isFinite() })
    val rightmostLeft = (widthPx - labelWidthPx).coerceAtLeast(0f)
    val positions = centersPx.map { (it - labelWidthPx / 2f).coerceIn(0f, rightmostLeft) }
    val overlap = positions.sorted().zipWithNext().any { (left, next) -> next < left + labelWidthPx }
    return TerraceLabelLayout(positions, overlap || (centersPx.isNotEmpty() && widthPx < labelWidthPx))
}

data class TerraceWeekLayout(
    val bounds: YardBounds,
    val widthPx: Float,
    /** Plant band only; grass, soil, chips and day labels are laid out below this band by the UI. */
    val heightPx: Float,
    val groundPx: Float,
    val days: List<TerraceDayZone>,
    /** Paint order: all back plants first, then front plants, each in stable day/time/id order. */
    val plants: List<TerracePlant>,
    val overflows: List<TerraceOverflow>,
    /** Whole Monday-Sunday week, including dates outside the selected month. Samples are absent. */
    val summary: YardSummary,
)

fun weekStart(time: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
    periodOf(time, YardPeriod.WEEK, timeZone)

/** [month] is 1-based. Includes every full Monday-Sunday week that touches this month. */
fun weeksOfMonth(year: Int, month: Int, timeZone: TimeZone = TimeZone.getDefault()): List<Long> {
    require(year > 0) { "year must be positive" }
    require(month in 1..12) { "month must be 1-based" }
    val first = GregorianCalendar(timeZone, Locale.ROOT).apply {
        clear(); set(year, month - 1, 1, 0, 0, 0)
    }.timeInMillis
    val monthBounds = periodBounds(first, YardPeriod.MONTH, timeZone)
    var start = weekStart(first, timeZone)
    return buildList {
        while (start < monthBounds.endExclusive) {
            add(start)
            val next = periodBounds(start, YardPeriod.WEEK, timeZone).endExclusive
            check(next > start) { "Calendar did not advance to the next week" }
            start = next
        }
    }
}

fun terraceWeeks(at: Long, period: YardPeriod, timeZone: TimeZone = TimeZone.getDefault()): List<Long> {
    if (period == YardPeriod.WEEK) return listOf(weekStart(at, timeZone))
    val calendar = terraceCalendar(at, timeZone)
    return weeksOfMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, timeZone)
}

fun terracePlantKind(item: WishItem, now: Long): TerracePlantKind = when (yardItemState(item, now)) {
    YardItemState.MOUND -> TerracePlantKind.MOUND
    YardItemState.SPROUT -> TerracePlantKind.SPROUT
    YardItemState.PIT -> TerracePlantKind.PIT
    YardItemState.TREE -> when {
        item.decisionPriceWon == null || item.decisionPriceWon < 50_000L -> TerracePlantKind.TREE_SMALL
        item.decisionPriceWon < 200_000L -> TerracePlantKind.TREE_BIG
        else -> TerracePlantKind.TREE_FRUIT
    }
}

/**
 * Pure layout for a single weekly plant band. All returned geometry uses pixels.
 * Pass 44.dp.toPx() as [basePx] from Compose; the 10 dp back-row lift scales with it.
 * The source mockup uses weighted day slots and overlapping plants. We preserve that grammar,
 * while clamping a state-independent plant anchor at the strip edges so growing a tree cannot
 * push its drawing outside the strip or move the anchor. Exceptionally tiny widths scale the
 * complete plant band uniformly; callers should still provide useful accessible touch targets.
 */
fun layoutWeek(
    items: List<WishItem>,
    at: Long,
    widthPx: Float,
    now: Long,
    basePx: Float = 44f,
    timeZone: TimeZone = TimeZone.getDefault(),
): TerraceWeekLayout {
    require(widthPx.isFinite() && widthPx >= 0f) { "widthPx must be finite and nonnegative" }
    require(basePx.isFinite() && basePx > 0f) { "basePx must be finite and positive" }
    val bounds = periodBounds(at, YardPeriod.WEEK, timeZone)
    val actual = items.filterNot { it.isSample }
    val summary = yardSummary(actual, bounds.startInclusive, YardPeriod.WEEK, now, timeZone)
    val today = terraceDayStart(now, timeZone)
    val dayStarts = (0..7).map { offset ->
        terraceCalendar(bounds.startInclusive, timeZone).apply {
            add(Calendar.DAY_OF_MONTH, offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val grouped = summary.realItems.filterNot { it.decision == Decision.BUY && !it.hasVisiblePit() }
        .groupBy { terraceDayStart(it.createdAt, timeZone) }
    val weights = (0..6).map { index ->
        val count = grouped[dayStarts[index]].orEmpty().size
        if (count == 0) .55f else minOf(count, 3).toFloat()
    }
    val totalWeight = weights.sum()
    val maxPlantSize = basePx * TerracePlantKind.TREE_FRUIT.sizeFactor
    require(maxPlantSize.isFinite()) { "basePx is too large" }
    val scale = minOf(1f, widthPx / maxPlantSize)
    val ground = maxPlantSize * scale
    val backLift = basePx * (10f / 44f) * scale
    val edgeInset = ground / 2f
    val plants = mutableListOf<TerracePlant>()
    val overflows = mutableListOf<TerraceOverflow>()
    var left = 0f
    val days = (0..6).map { index ->
        val start = dayStarts[index]
        val dayItems = grouped[start].orEmpty().sortedWith(compareBy<WishItem> { it.createdAt }.thenBy { it.id })
        // Force the final boundary to exactly widthPx rather than accumulating Float error.
        val right = if (index == 6) widthPx else (left + widthPx * (weights[index] / totalWeight)).coerceAtMost(widthPx)
        val slotWidth = (right - left).coerceAtLeast(0f)
        val day = TerraceDayZone(start, dayStarts[index + 1], index,
            terraceCalendar(start, timeZone).get(Calendar.DAY_OF_MONTH), left, slotWidth, weights[index], dayItems,
            isToday = start == today, isFuture = start > today)
        listOf(TerraceLayer.BACK to dayItems.drop(3).take(3), TerraceLayer.FRONT to dayItems.take(3)).forEach { (layer, row) ->
            row.forEachIndexed { position, item ->
                val isBack = layer == TerraceLayer.BACK
                val kind = terracePlantKind(item, now)
                val size = basePx * kind.sizeFactor * (if (isBack) .78f else 1f) * scale
                val preferredCenter = left + slotWidth * (position + .5f) / row.size + if (isBack) slotWidth * .12f else 0f
                val center = preferredCenter.coerceIn(edgeInset, (widthPx - edgeInset).coerceAtLeast(edgeInset))
                val bottom = ground - if (isBack) backLift else 0f
                plants += TerracePlant(item, start, kind, layer, (center - size / 2f).coerceAtLeast(0f), (bottom - size).coerceAtLeast(0f), size)
            }
        }
        if (dayItems.size > 6) overflows += TerraceOverflow(start, dayItems.size - 6, day.centerXPx)
        left = right
        day
    }
    return TerraceWeekLayout(bounds, widthPx, ground, ground, days,
        plants.sortedWith(compareBy<TerracePlant> { it.layer.ordinal }.thenBy { it.item.createdAt }.thenBy { it.item.id }),
        overflows, summary)
}

private fun terraceCalendar(time: Long, zone: TimeZone) = GregorianCalendar(zone, Locale.ROOT).apply { timeInMillis = time }
private fun terraceDayStart(time: Long, zone: TimeZone): Long = terraceCalendar(time, zone).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
