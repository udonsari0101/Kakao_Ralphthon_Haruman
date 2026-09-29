package org.ralphthon.haruman_ralphthon.ui

import org.json.JSONArray
import org.json.JSONObject
import org.ralphthon.haruman_ralphthon.data.ItemJsonCodec
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.data.skippedTotal
import java.util.UUID

enum class ReviewAction { SKIP, BUY, LATER }
data class ReviewRecord(val before: WishItem, val after: WishItem, val action: ReviewAction)
data class ReviewSession(
    val id: String = UUID.randomUUID().toString(),
    val ids: List<String>,
    val index: Int = 0,
    val results: List<ReviewRecord> = emptyList(),
    val undoUntil: Long = 0,
    val celebrationConsumed: Boolean = false,
) {
    val complete: Boolean get() = index >= ids.size
    val skipped: List<WishItem> get() = results.filter { it.action == ReviewAction.SKIP }.map { it.after }
    val bought: List<WishItem> get() = results.filter { it.action == ReviewAction.BUY }.map { it.after }
    val later: List<WishItem> get() = results.filter { it.action == ReviewAction.LATER }.map { it.after }
    val total: Long get() = skippedTotal(skipped)
    val hasExamples: Boolean get() = results.any { it.after.isSample }
    val realSkipCount: Int get() = skipped.count { !it.isSample }
    val hasKnownSavings: Boolean get() = skipped.any { !it.isSample && it.decisionPriceWon != null }
    fun encoded(): String = JSONObject().apply {
        put("id", id); put("ids", JSONArray(ids)); put("index", index)
        put("undoUntil", undoUntil); put("celebrationConsumed", celebrationConsumed)
        put("records", JSONArray().apply { results.forEach { record ->
            put(JSONObject().apply {
                put("before", ItemJsonCodec.encode(listOf(record.before)))
                put("after", ItemJsonCodec.encode(listOf(record.after)))
                put("action", record.action.name)
            })
        } })
    }.toString()

    companion object {
        fun decode(value: String?): ReviewSession? = value?.let { runCatching {
            val json = JSONObject(it)
            val ids = json.getJSONArray("ids").let { array -> List(array.length()) { index -> array.getString(index) } }
            val records = json.getJSONArray("records").let { array -> List(array.length()) { index ->
                val record = array.getJSONObject(index)
                ReviewRecord(ItemJsonCodec.decode(record.getString("before")).items.single(),
                    ItemJsonCodec.decode(record.getString("after")).items.single(), ReviewAction.valueOf(record.getString("action")))
            } }
            ReviewSession(json.getString("id"), ids, json.getInt("index").coerceIn(0, ids.size), records,
                json.optLong("undoUntil"), json.optBoolean("celebrationConsumed"))
        }.getOrNull() }
    }
}
