package org.ralphthon.haruman_ralphthon.data

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

enum class YardPeriod(val label: String) { WEEK("한 주"), MONTH("한 달") }
enum class YardItemState { MOUND, SPROUT, TREE, PIT }

data class YardBounds(val startInclusive: Long, val endExclusive: Long) {
    operator fun contains(time: Long): Boolean = time >= startInclusive && time < endExclusive
}

/** Calendar keeps API 24 support and advances local dates across DST, not fixed milliseconds. */
fun periodOf(time: Long, period: YardPeriod, timeZone: TimeZone = TimeZone.getDefault()): Long {
    val date = GregorianCalendar(timeZone, Locale.ROOT).apply { timeInMillis = time }
    when (period) {
        YardPeriod.MONTH -> date.set(Calendar.DAY_OF_MONTH, 1)
        YardPeriod.WEEK -> date.add(Calendar.DAY_OF_MONTH, -((date.get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }
    date.set(Calendar.HOUR_OF_DAY, 0)
    date.set(Calendar.MINUTE, 0)
    date.set(Calendar.SECOND, 0)
    date.set(Calendar.MILLISECOND, 0)
    return date.timeInMillis
}

fun periodBounds(time: Long, period: YardPeriod, timeZone: TimeZone = TimeZone.getDefault()): YardBounds {
    val start = periodOf(time, period, timeZone)
    val end = GregorianCalendar(timeZone, Locale.ROOT).apply {
        timeInMillis = start
        add(if (period == YardPeriod.WEEK) Calendar.WEEK_OF_YEAR else Calendar.MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return YardBounds(start, end)
}

/** Oldest to current, inclusive. Future-only records still expose just the current page. */
fun periodsBetween(first: Long, now: Long, period: YardPeriod, timeZone: TimeZone = TimeZone.getDefault()): List<Long> {
    val current = periodOf(now, period, timeZone)
    var cursor = periodOf(minOf(first, now), period, timeZone)
    return buildList {
        while (cursor <= current) {
            add(cursor)
            if (cursor == current) break
            val next = periodBounds(cursor, period, timeZone).endExclusive
            check(next > cursor) { "Calendar did not advance to the next period." }
            cursor = next
        }
    }
}

fun yardItemState(item: WishItem, now: Long): YardItemState = when (item.decision) {
    Decision.SKIP -> YardItemState.TREE
    Decision.BUY -> YardItemState.PIT
    Decision.PENDING -> if (item.ready(now)) YardItemState.SPROUT else YardItemState.MOUND
}

fun cheekStage(pendingCount: Int): Int = when {
    pendingCount <= 0 -> 0
    pendingCount <= 2 -> 1
    pendingCount <= 5 -> 2
    pendingCount <= 9 -> 3
    else -> 4
}

/** Visual counts include labeled examples; all financial totals and unknown counts exclude them. */
data class YardSummary(
    val bounds: YardBounds,
    val items: List<WishItem>,
    val budgetItems: List<WishItem>,
    val now: Long,
) {
    val realItems = items.filterNot { it.isSample }
    val sampleCount: Int = items.size - realItems.size
    val treeCount: Int = items.count { it.decision == Decision.SKIP }
    val pitCount: Int = items.count { it.decision == Decision.BUY }
    val sproutCount: Int = items.count { it.ready(now) }
    val moundCount: Int = items.count { it.decision == Decision.PENDING && !it.ready(now) }
    val realTreeCount: Int = realItems.count { it.decision == Decision.SKIP }
    val realPitCount: Int = realItems.count { it.decision == Decision.BUY }
    val realSproutCount: Int = realItems.count { it.ready(now) }
    val realMoundCount: Int = realItems.count { it.decision == Decision.PENDING && !it.ready(now) }
    val savedWon: Long = sumDecisionAmounts(realItems.filter { it.decision == Decision.SKIP })
    val boughtWon: Long = sumDecisionAmounts(realItems.filter { it.decision == Decision.BUY })
    val unknownSavedCount: Int = realItems.count { it.decision == Decision.SKIP && it.knownDecisionAmount() == null }
    val unknownBoughtCount: Int = realItems.count { it.decision == Decision.BUY && it.knownDecisionAmount() == null }
    val budgetBoughtWon: Long = sumDecisionAmounts(budgetItems)
    val budgetUnknownCount: Int = budgetItems.count { it.knownDecisionAmount() == null }
    val largestTree: WishItem? = realItems.filter { it.decision == Decision.SKIP && it.knownDecisionAmount() != null }
        .maxByOrNull { it.knownDecisionAmount()!! }
}

/** Garden membership is CREATED time; budget membership is BUY decision time. Never substitute one. */
fun yardSummary(
    items: List<WishItem>,
    at: Long,
    period: YardPeriod,
    now: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): YardSummary {
    val bounds = periodBounds(at, period, timeZone)
    return YardSummary(
        bounds = bounds,
        items = items.filter { it.createdAt in bounds }.sortedWith(compareBy<WishItem> { it.createdAt }.thenBy { it.id }),
        budgetItems = items.filter { !it.isSample && it.decision == Decision.BUY && it.decidedAt?.let { time -> time in bounds } == true }
            .sortedWith(compareBy<WishItem> { it.decidedAt }.thenBy { it.id }),
        now = now,
    )
}

fun yardPeriodKey(period: YardPeriod, start: Long): String = "${period.name}:$start"

private fun WishItem.knownDecisionAmount(): Long? = decisionPriceWon?.takeIf { it >= 0L }
private fun sumDecisionAmounts(items: List<WishItem>): Long = items.mapNotNull { it.knownDecisionAmount() }
    .fold(0L) { total, amount -> if (total > Long.MAX_VALUE - amount) Long.MAX_VALUE else total + amount }
