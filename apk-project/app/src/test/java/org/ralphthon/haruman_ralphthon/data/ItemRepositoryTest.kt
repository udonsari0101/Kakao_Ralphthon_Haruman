package org.ralphthon.haruman_ralphthon.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ItemRepositoryTest {
    private val item = WishItem("user-1", "상품", 25_000L, Platform.COUPANG, "https://coupang.com/vp/products/1", "default", 100L, 200L)

    @Test
    fun newStorageStartsEmptyAndDoesNotWriteSampleRecords() {
        val disk = MemoryDisk()
        assertTrue(ItemRepository(disk).items().isEmpty())
        assertNull(disk.stored)
        assertEquals(0, disk.writes)
    }

    @Test
    fun acquisitionReplayAfterRepositoryRecreationReturnsOriginalItem() {
        val disk = MemoryDisk()
        val first = item.copy(acquisitionId = "acquisition-1")
        assertTrue(ItemRepository(disk).saveNewItemOnce(first) is SaveItemResult.Saved)
        val replay = ItemRepository(disk).saveNewItemOnce(first.copy(id = "different-id", name = "늦은 결과"))
        assertEquals(first, (replay as SaveItemResult.AlreadySaved).item)
        assertEquals(listOf(first), ItemRepository(disk).items())
        assertEquals(1, disk.writes)
    }

    @Test
    fun failedCommitDoesNotClaimSuccessOrSuppressRetry() {
        val disk = MemoryDisk().apply { failWrites = true }
        val repository = ItemRepository(disk)
        assertEquals(SaveItemResult.Failed, repository.saveNewItemOnce(item))
        assertTrue(repository.items().isEmpty())
        disk.failWrites = false
        assertTrue(repository.saveNewItemOnce(item) is SaveItemResult.Saved)
        assertEquals(listOf(item), repository.items())
    }

    @Test
    fun legacyDecisionsMigrateOnceAndPreserveInstagramAndSamples() {
        val legacy = legacyJson(item.copy(platform = Platform.INSTAGRAM, decision = Decision.SKIP))
        val sample = legacyJson(item.copy(id = "sample-history", decision = Decision.SKIP))
        val disk = MemoryDisk(JSONArray().put(legacy).put(sample).toString())
        val repository = ItemRepository(disk)
        val loaded = repository.items()
        assertEquals(Platform.INSTAGRAM, loaded[0].platform)
        assertEquals(InputSource.LEGACY, loaded[0].inputSource)
        assertEquals(25_000L, loaded[0].decisionPriceWon)
        assertTrue(loaded[1].isSample)
        assertEquals(25_000L, skippedTotal(loaded))
        assertEquals(1, disk.writes)
        repository.items()
        assertEquals(1, disk.writes)
        assertTrue(repository.saveItems(loaded.map { it.copy(priceWon = 900_000L) }))
        assertEquals(25_000L, skippedTotal(ItemRepository(disk).items()))
    }

    @Test
    fun explicitlyUnknownDecisionAmountRemainsNullAfterPriceEdits() {
        val unknown = item.copy(decision = Decision.SKIP, priceWon = 30_000L, decisionPriceWon = null)
        val loaded = ItemJsonCodec.decode(ItemJsonCodec.encode(listOf(unknown))).items.single()
        assertNull(loaded.decisionPriceWon)
        assertEquals(0L, skippedTotal(listOf(loaded)))
    }

    @Test
    fun snapshotsManualFieldsImagePreferenceAndAcquisitionRoundTrip() {
        val full = item.copy(
            description = "사용자가 적은 설명", userMemo = "고민 메모", imageUrl = "https://thumbnail.coupangcdn.com/a.jpg",
            imageHidden = true, inputSource = InputSource.LINK_SHARE, productId = "1", itemId = "2", vendorItemId = "3",
            acquisitionId = "work-1", productSnapshot = ProductSnapshot(
                name = "API 원본", priceWon = 10_000L, currency = "KRW", source = "coupang", originalUrl = item.url,
                priceType = "coupon", priceNote = "조건 확인", optionLabel = "검정", productId = "1", itemId = "2",
            ),
        )
        assertEquals(full, ItemJsonCodec.decode(ItemJsonCodec.encode(listOf(full))).items.single())
    }

    @Test
    fun malformedStorageIsPreservedAndNewWritesFailInsteadOfErasingIt() {
        val stored = JSONArray().put(legacyJson(item)).put(JSONObject().put("broken", true)).toString()
        val disk = MemoryDisk(stored)
        val repository = ItemRepository(disk)
        assertEquals(1, repository.items().size)
        assertEquals(stored, disk.stored)
        assertEquals(SaveItemResult.Failed, repository.saveNewItemOnce(item.copy(id = "new")))
        assertFalse(repository.saveItems(emptyList()))
        assertEquals(stored, disk.stored)
        assertEquals(0, disk.writes)
    }

    @Test
    fun desireIsOptionalAndUnknownFutureValuesDoNotDiscardLegacyRecords() {
        for (desire in listOf(null, Desire.FIRE, Desire.LIKE, Desire.MEH)) {
            val desired = item.copy(desire = desire)
            assertEquals(desire, ItemJsonCodec.decode(ItemJsonCodec.encode(listOf(desired))).items.single().desire)
        }
        assertNull(ItemJsonCodec.decode(JSONArray().put(legacyJson(item)).toString()).items.single().desire)
        val unknown = legacyJson(item).put("desire", "FUTURE_DESIRE")
        assertNull(ItemJsonCodec.decode(JSONArray().put(unknown).toString()).items.single().desire)
        val invalidType = legacyJson(item).put("desire", JSONObject())
        assertNull(ItemJsonCodec.decode(JSONArray().put(invalidType).toString()).items.single().desire)
    }

    private fun legacyJson(value: WishItem): JSONObject = JSONObject().apply {
        put("id", value.id); put("name", value.name); put("priceWon", value.priceWon ?: JSONObject.NULL)
        put("platform", value.platform.name); put("url", value.url); put("artwork", value.artwork)
        put("createdAt", value.createdAt); put("cooldownUntil", value.cooldownUntil)
        put("decision", value.decision.name); put("decidedAt", value.decidedAt ?: JSONObject.NULL)
    }

    private class MemoryDisk(var stored: String? = null) : ItemPersistence {
        var writes = 0
        var failWrites = false
        override fun read(): String? = stored
        override fun write(value: String): Boolean {
            if (failWrites) return false
            stored = value
            writes++
            return true
        }
    }
}
