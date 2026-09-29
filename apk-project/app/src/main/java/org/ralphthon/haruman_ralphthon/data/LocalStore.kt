package org.ralphthon.haruman_ralphthon.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Platform(val label: String) {
    COUPANG("쿠팡"), INSTAGRAM("인스타그램 · 이전 기록"), MANUAL("직접 입력"),
}
enum class Decision { PENDING, BUY, SKIP }
enum class InputSource { LINK_INPUT, LINK_SHARE, MANUAL, LEGACY }
enum class Desire(val label: String) { FIRE("🔥 엄청"), LIKE("🙂 꽤"), MEH("🤔 글쎄") }
enum class AcornStage { BURIED, SPROUT, READY }
enum class ThemePreference(val label: String) {
    SYSTEM("시스템 설정"), LIGHT("라이트"), DARK("다크"),
}

/** API values stay separate from user edits. No credentials or raw response body are stored. */
data class ProductSnapshot(
    val name: String? = null,
    val priceWon: Long? = null,
    val currency: String? = null,
    val imageUrl: String? = null,
    val priceType: String? = null,
    val priceNote: String? = null,
    val optionLabel: String? = null,
    val source: String? = null,
    val originalUrl: String = "",
    val productUrl: String? = null,
    val productId: String? = null,
    val itemId: String? = null,
    val vendorItemId: String? = null,
    val fetchedAt: String? = null,
)

data class WishItem(
    val id: String,
    val name: String,
    val priceWon: Long?,
    val platform: Platform,
    val url: String,
    val artwork: String,
    val createdAt: Long,
    val cooldownUntil: Long,
    val decision: Decision = Decision.PENDING,
    val decidedAt: Long? = null,
    val description: String = "",
    val userMemo: String = "",
    val imageUrl: String? = null,
    val imageHidden: Boolean = false,
    val decisionPriceWon: Long? = null,
    val isSample: Boolean = id.startsWith("sample-"),
    val inputSource: InputSource = InputSource.LEGACY,
    val productId: String? = null,
    val itemId: String? = null,
    val vendorItemId: String? = null,
    val acquisitionId: String? = null,
    val productSnapshot: ProductSnapshot? = null,
    val desire: Desire? = null,
) {
    fun ready(now: Long): Boolean = decision == Decision.PENDING && now >= cooldownUntil
    fun remainingMillis(now: Long): Long = if (now >= cooldownUntil) 0L
        else (cooldownUntil.toDouble() - now.toDouble()).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
    fun progress(now: Long): Float {
        if (cooldownUntil <= createdAt) return if (now >= cooldownUntil) 1f else 0f
        return ((now.toDouble() - createdAt) / (cooldownUntil.toDouble() - createdAt))
            .toFloat().coerceIn(0f, 1f)
    }
}

fun acornStage(item: WishItem, now: Long): AcornStage? = when {
    item.decision != Decision.PENDING -> null
    item.ready(now) -> AcornStage.READY
    item.progress(now) >= 0.5f -> AcornStage.SPROUT
    else -> AcornStage.BURIED
}

/** A decision is final. Later edits preserve the recorded history amount. */
fun decide(item: WishItem, choice: Decision, now: Long): WishItem =
    if (choice == Decision.PENDING || !item.ready(now)) item
    else item.copy(decision = choice, decidedAt = now, decisionPriceWon = item.priceWon)

fun extend(item: WishItem, hours: Int, now: Long): WishItem {
    require(hours in 1..MAX_COOLDOWN_HOURS) { "Invalid cooldown hours." }
    return if (item.decision != Decision.PENDING) item
    else item.copy(cooldownUntil = cooldownDeadline(now, hours))
}

fun cooldownDeadline(now: Long, hours: Int): Long {
    require(hours in 0..MAX_COOLDOWN_HOURS) { "Invalid cooldown hours." }
    val duration = hours * HOUR_MILLIS
    return if (now > Long.MAX_VALUE - duration) Long.MAX_VALUE else now + duration
}

fun skippedTotal(items: List<WishItem>): Long = items.asSequence()
    .filter { it.decision == Decision.SKIP && !it.isSample }
    .mapNotNull { it.decisionPriceWon?.takeIf { price -> price >= 0L } }
    .fold(0L) { sum, amount -> if (sum > Long.MAX_VALUE - amount) Long.MAX_VALUE else sum + amount }

sealed interface SaveItemResult {
    data class Saved(val item: WishItem) : SaveItemResult
    data class AlreadySaved(val item: WishItem) : SaveItemResult
    data object Failed : SaveItemResult
}

/** New installs start empty. Old platform records are preserved, including Instagram and samples. */
class LocalStore(context: Context, prefsName: String = "haruman_local_v1") {
    private val preferences = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val repository = ItemRepository(object : ItemPersistence {
        override fun read(): String? = preferences.getString("items", null)
        override fun write(value: String): Boolean {
            val previous = read()
            val successful = runCatching {
                preferences.edit().putString("items", value).putInt("schema_version", 2).commit()
            }.getOrDefault(false)
            if (!successful) {
                // SharedPreferences changes its in-memory map before reporting a disk failure.
                // Restore the prior map so a failed save cannot look like an already-saved item.
                runCatching {
                    preferences.edit().apply {
                        if (previous == null) remove("items") else putString("items", previous)
                    }.commit()
                }
            }
            return successful
        }
    })
    var onboardingDone: Boolean
        get() = preferences.getBoolean("onboarding_done", false)
        set(value) { preferences.edit().putBoolean("onboarding_done", value).apply() }
    var defaultCooldownHours: Int
        get() = preferences.getInt("default_cooldown_hours", 24).coerceIn(1, MAX_COOLDOWN_HOURS)
        set(value) {
            require(value in 1..MAX_COOLDOWN_HOURS) { "Invalid cooldown hours." }
            preferences.edit().putInt("default_cooldown_hours", value).apply()
        }
    var themePreference: ThemePreference
        get() = runCatching {
            ThemePreference.valueOf(preferences.getString("theme_preference", ThemePreference.SYSTEM.name)!!)
        }.getOrDefault(ThemePreference.SYSTEM)
        set(value) { preferences.edit().putString("theme_preference", value.name).apply() }
    var mascotBubbles: Boolean
        get() = preferences.getBoolean("mascot_bubbles", true)
        set(value) { preferences.edit().putBoolean("mascot_bubbles", value).apply() }
    var mascotReduceMotion: Boolean
        get() = preferences.getBoolean("mascot_reduce_motion", false)
        set(value) { preferences.edit().putBoolean("mascot_reduce_motion", value).apply() }

    fun items(): List<WishItem> = repository.items()
    fun saveItems(items: List<WishItem>): Boolean = repository.saveItems(items)
    fun saveNewItemOnce(item: WishItem): SaveItemResult = repository.saveNewItemOnce(item)
}

internal interface ItemPersistence {
    fun read(): String?
    fun write(value: String): Boolean
}

/** The same repository runs over preferences in the app and an in-memory disk substitute in tests. */
internal class ItemRepository(private val persistence: ItemPersistence) {
    fun items(): List<WishItem> = synchronized(STORE_LOCK) {
        val stored = persistence.read() ?: return@synchronized emptyList()
        val decoded = ItemJsonCodec.decode(stored)
        if (decoded.needsMigration && !decoded.hasInvalidRecords) saveItems(decoded.items)
        decoded.items
    }

    fun saveItems(items: List<WishItem>): Boolean = synchronized(STORE_LOCK) {
        runCatching {
            val stored = persistence.read()
            // Avoid discarding unreadable existing data during either migration or a user save.
            if (stored != null && ItemJsonCodec.decode(stored).hasInvalidRecords) false
            else persistence.write(ItemJsonCodec.encode(items))
        }.getOrDefault(false)
    }

    /** Item and acquisition ID use one durable JSON write; a replay returns the saved item. */
    fun saveNewItemOnce(item: WishItem): SaveItemResult = synchronized(STORE_LOCK) {
        val stored = runCatching { persistence.read() }.getOrElse { return@synchronized SaveItemResult.Failed }
        val decoded = stored?.let(ItemJsonCodec::decode)
        if (decoded?.hasInvalidRecords == true) return@synchronized SaveItemResult.Failed
        val existing = decoded?.items.orEmpty()
        val already = existing.firstOrNull {
            it.id == item.id || (!item.acquisitionId.isNullOrBlank() && it.acquisitionId == item.acquisitionId)
        }
        when {
            already != null -> SaveItemResult.AlreadySaved(already)
            saveItems(existing + item) -> SaveItemResult.Saved(item)
            else -> SaveItemResult.Failed
        }
    }
    companion object { private val STORE_LOCK = Any() }
}

internal data class DecodedItems(val items: List<WishItem>, val needsMigration: Boolean, val hasInvalidRecords: Boolean)
internal object ItemJsonCodec {
    fun encode(items: List<WishItem>): String = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
    fun decode(stored: String): DecodedItems {
        val array = runCatching { JSONArray(stored) }.getOrNull() ?: return DecodedItems(emptyList(), false, true)
        var migration = false
        var invalid = false
        val items = buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                val item = runCatching { json?.toWishItem() }.getOrNull()
                if (item == null) invalid = true else {
                    add(item)
                    if (!json!!.has("decisionPriceWon") || !json.has("inputSource")) migration = true
                }
            }
        }
        return DecodedItems(items, migration, invalid)
    }
}

private fun WishItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); nullable("priceWon", priceWon)
    put("platform", platform.name); put("url", url); put("artwork", artwork)
    put("createdAt", createdAt); put("cooldownUntil", cooldownUntil)
    put("decision", decision.name); nullable("decidedAt", decidedAt)
    put("description", description); put("userMemo", userMemo)
    nullable("imageUrl", imageUrl); put("imageHidden", imageHidden)
    nullable("decisionPriceWon", decisionPriceWon); put("isSample", isSample)
    put("inputSource", inputSource.name); nullable("productId", productId)
    nullable("itemId", itemId); nullable("vendorItemId", vendorItemId)
    nullable("acquisitionId", acquisitionId); nullable("productSnapshot", productSnapshot?.toJson())
    nullable("desire", desire?.name)
}
private fun JSONObject.toWishItem(): WishItem {
    val decision = Decision.valueOf(optString("decision", Decision.PENDING.name))
    val price = nullableLong("priceWon")
    val id = getString("id")
    return WishItem(
        id = id, name = getString("name"), priceWon = price,
        platform = Platform.valueOf(getString("platform")), url = optString("url", ""),
        artwork = optString("artwork", "default"), createdAt = getLong("createdAt"), cooldownUntil = getLong("cooldownUntil"),
        decision = decision, decidedAt = nullableLong("decidedAt"), description = optString("description", ""),
        userMemo = optString("userMemo", ""), imageUrl = nullableString("imageUrl"), imageHidden = optBoolean("imageHidden", false),
        // Explicit null remains unknown even if the user later edits priceWon.
        decisionPriceWon = if (has("decisionPriceWon")) nullableLong("decisionPriceWon")
            else price.takeIf { decision != Decision.PENDING },
        isSample = optBoolean("isSample", id.startsWith("sample-")),
        inputSource = runCatching { InputSource.valueOf(optString("inputSource", InputSource.LEGACY.name)) }.getOrDefault(InputSource.LEGACY),
        productId = nullableString("productId"), itemId = nullableString("itemId"), vendorItemId = nullableString("vendorItemId"),
        acquisitionId = nullableString("acquisitionId"), productSnapshot = optJSONObject("productSnapshot")?.toSnapshot(),
        desire = runCatching { nullableString("desire")?.let(Desire::valueOf) }.getOrNull(),
    )
}
internal fun ProductSnapshot.toJson(): JSONObject = JSONObject().apply {
    nullable("name", name); nullable("priceWon", priceWon); nullable("currency", currency)
    nullable("imageUrl", imageUrl); nullable("priceType", priceType); nullable("priceNote", priceNote)
    nullable("optionLabel", optionLabel); nullable("source", source); put("originalUrl", originalUrl)
    nullable("productUrl", productUrl); nullable("productId", productId); nullable("itemId", itemId)
    nullable("vendorItemId", vendorItemId); nullable("fetchedAt", fetchedAt)
}
internal fun JSONObject.toSnapshot(): ProductSnapshot = ProductSnapshot(
    name = nullableString("name"), priceWon = nullableLong("priceWon"), currency = nullableString("currency"),
    imageUrl = nullableString("imageUrl"), priceType = nullableString("priceType"), priceNote = nullableString("priceNote"),
    optionLabel = nullableString("optionLabel"), source = nullableString("source"), originalUrl = optString("originalUrl", ""),
    productUrl = nullableString("productUrl"), productId = nullableString("productId"), itemId = nullableString("itemId"),
    vendorItemId = nullableString("vendorItemId"), fetchedAt = nullableString("fetchedAt"),
)
private fun JSONObject.nullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

const val HOUR_MILLIS = 3_600_000L
const val MAX_COOLDOWN_HOURS = 24 * 365
