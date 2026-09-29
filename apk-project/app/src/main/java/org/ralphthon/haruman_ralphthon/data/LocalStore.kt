package org.ralphthon.haruman_ralphthon.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

enum class Platform(val label: String) {
    COUPANG("쿠팡"),
    INSTAGRAM("인스타그램"),
}

enum class Decision { PENDING, BUY, SKIP }

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
) {
    fun ready(now: Long): Boolean = decision == Decision.PENDING && now >= cooldownUntil

    fun remainingMillis(now: Long): Long = (cooldownUntil - now).coerceAtLeast(0L)

    fun progress(now: Long): Float {
        if (cooldownUntil <= createdAt) return if (now >= cooldownUntil) 1f else 0f
        return ((now.toDouble() - createdAt) / (cooldownUntil.toDouble() - createdAt))
            .toFloat().coerceIn(0f, 1f)
    }
}

/** A decision can only be made once, after the item's cooling period has ended. */
fun decide(item: WishItem, choice: Decision, now: Long): WishItem =
    if (choice == Decision.PENDING || !item.ready(now)) item
    else item.copy(decision = choice, decidedAt = now)

/** Start another cooling period for an undecided item. */
fun extend(item: WishItem, hours: Int, now: Long): WishItem {
    require(hours > 0) { "Cooldown hours must be positive." }
    return if (item.decision != Decision.PENDING) item
    else item.copy(cooldownUntil = now + hours.toLong() * HOUR_MILLIS)
}

fun skippedTotal(items: List<WishItem>): Long = items.asSequence()
    .filter { it.decision == Decision.SKIP }
    .sumOf { it.priceWon?.coerceAtLeast(0L) ?: 0L }

/** Read a supported link from the text Android shares, without fetching that URL. */
fun parseSupportedUrl(text: String): Pair<Platform, String>? {
    for (match in SHARED_URL.findAll(text)) {
        val candidate = match.value.trimEnd(')', ']', '}', ',', '。')
        val uri = runCatching { URI(candidate) }.getOrNull() ?: continue
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) continue
        if (uri.rawUserInfo != null) continue
        val host = uri.host?.lowercase(Locale.ROOT) ?: continue
        val platform = when {
            host == "coupang.com" || host.endsWith(".coupang.com") -> Platform.COUPANG
            host == "instagram.com" || host.endsWith(".instagram.com") -> Platform.INSTAGRAM
            else -> continue
        }
        return platform to candidate
    }
    return null
}

/** Device-only storage. Sample links are empty, and no network requests are made here. */
class LocalStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("haruman_local_v1", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = preferences.getBoolean("onboarding_done", false)
        set(value) { preferences.edit().putBoolean("onboarding_done", value).apply() }

    var defaultCooldownHours: Int
        get() = preferences.getInt("default_cooldown_hours", 24).coerceAtLeast(1)
        set(value) {
            require(value > 0) { "Cooldown hours must be positive." }
            preferences.edit().putInt("default_cooldown_hours", value).apply()
        }

    fun items(): List<WishItem> {
        val stored = preferences.getString("items", null)
        if (stored == null) return sampleItems(System.currentTimeMillis()).also(::saveItems)
        val array = runCatching { JSONArray(stored) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = runCatching { array.getJSONObject(index).toWishItem() }.getOrNull()
                if (item != null) add(item)
            }
        }
    }

    fun saveItems(items: List<WishItem>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        preferences.edit().putString("items", array.toString()).apply()
    }
}

private fun WishItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("priceWon", priceWon ?: JSONObject.NULL)
    put("platform", platform.name)
    put("url", url)
    put("artwork", artwork)
    put("createdAt", createdAt)
    put("cooldownUntil", cooldownUntil)
    put("decision", decision.name)
    put("decidedAt", decidedAt ?: JSONObject.NULL)
}

private fun JSONObject.toWishItem(): WishItem = WishItem(
    id = getString("id"),
    name = getString("name"),
    priceWon = if (isNull("priceWon")) null else getLong("priceWon"),
    platform = Platform.valueOf(getString("platform")),
    url = optString("url", ""),
    artwork = optString("artwork", "default"),
    createdAt = getLong("createdAt"),
    cooldownUntil = getLong("cooldownUntil"),
    decision = Decision.valueOf(optString("decision", Decision.PENDING.name)),
    decidedAt = if (isNull("decidedAt")) null else getLong("decidedAt"),
)

private fun sampleItems(now: Long): List<WishItem> = listOf(
    WishItem(
        id = "sample-headphones",
        name = "노이즈 캔슬링 무선 헤드폰",
        priceWon = 329_000,
        platform = Platform.COUPANG,
        url = "",
        artwork = "headphones",
        createdAt = now - 27 * HOUR_MILLIS,
        cooldownUntil = now - 3 * HOUR_MILLIS,
    ),
    WishItem(
        id = "sample-lamp",
        name = "따뜻한 빛의 테이블 조명",
        priceWon = 58_000,
        platform = Platform.INSTAGRAM,
        url = "",
        artwork = "lamp",
        createdAt = now - 25 * HOUR_MILLIS,
        cooldownUntil = now - HOUR_MILLIS,
    ),
    WishItem(
        id = "sample-shoes",
        name = "데일리 러닝 스니커즈",
        priceWon = 129_000,
        platform = Platform.COUPANG,
        url = "",
        artwork = "shoes",
        createdAt = now - 6 * HOUR_MILLIS,
        cooldownUntil = now + 18 * HOUR_MILLIS,
    ),
    WishItem(
        id = "sample-hairdryer",
        name = "컴팩트 고속 헤어드라이어",
        priceWon = 89_000,
        platform = Platform.INSTAGRAM,
        url = "",
        artwork = "hairdryer",
        createdAt = now - 17 * HOUR_MILLIS,
        cooldownUntil = now + 7 * HOUR_MILLIS,
    ),
    WishItem(
        id = "sample-history-stand",
        name = "접이식 알루미늄 노트북 거치대",
        priceWon = 39_900,
        platform = Platform.COUPANG,
        url = "",
        artwork = "stand",
        createdAt = now - 96 * HOUR_MILLIS,
        cooldownUntil = now - 72 * HOUR_MILLIS,
        decision = Decision.SKIP,
        decidedAt = now - 70 * HOUR_MILLIS,
    ),
    WishItem(
        id = "sample-history-lamp",
        name = "침실 무드 조명",
        priceWon = 42_000,
        platform = Platform.INSTAGRAM,
        url = "",
        artwork = "lamp",
        createdAt = now - 72 * HOUR_MILLIS,
        cooldownUntil = now - 48 * HOUR_MILLIS,
        decision = Decision.SKIP,
        decidedAt = now - 47 * HOUR_MILLIS,
    ),
    WishItem(
        id = "sample-history-shoes",
        name = "가벼운 데일리 운동화",
        priceWon = 69_000,
        platform = Platform.COUPANG,
        url = "",
        artwork = "shoes",
        createdAt = now - 60 * HOUR_MILLIS,
        cooldownUntil = now - 36 * HOUR_MILLIS,
        decision = Decision.BUY,
        decidedAt = now - 34 * HOUR_MILLIS,
    ),
)

private const val HOUR_MILLIS = 3_600_000L
private val SHARED_URL = Regex("https?://[^\\s<>\"'\\p{Cntrl}]+", RegexOption.IGNORE_CASE)
