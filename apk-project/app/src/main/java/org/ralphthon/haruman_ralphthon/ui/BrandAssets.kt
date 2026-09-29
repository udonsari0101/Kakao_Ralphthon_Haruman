package org.ralphthon.haruman_ralphthon.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ralphthon.haruman_ralphthon.R
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.ui.mascot.Mascot
import org.ralphthon.haruman_ralphthon.ui.mascot.MascotPose
import org.ralphthon.haruman_ralphthon.ui.mascot.mascotMotionReduced
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/** The supplied wordmark is preserved; text is never generated into brand artwork. */
@Composable
fun BrandLogo(modifier: Modifier = Modifier) {
    val resource = if (LocalAppPalette.current.dark) R.drawable.haruman_wordmark_white
        else R.drawable.haruman_wordmark_color
    Image(
        painter = painterResource(resource), contentDescription = "하루만",
        modifier = modifier.size(width = 116.dp, height = 38.dp), contentScale = ContentScale.Fit
    )
}

/** Compatibility bridge: brand artwork stays in the launcher; app states use Moa and Tori. */
@Composable
fun CharacterArt(state: String, modifier: Modifier = Modifier) {
    val reducedMotion = mascotMotionReduced()
    val normalized = state.lowercase(Locale.ROOT)
    val review = normalized in setOf("needsreview", "failed", "interrupted", "error")
    val pose = when {
        normalized == "empty" -> MascotPose.MOA_SLEEP
        normalized == "loading" -> MascotPose.MOA_RECEIVE
        normalized == "success" -> MascotPose.MOA_HAPPY
        normalized == "celebrate" -> MascotPose.TORI_CHEER
        review -> MascotPose.TORI_PRESENT
        else -> MascotPose.MOA_IDLE
    }
    val announcement = when {
        normalized == "loading" -> "모아: 상품 정보를 가져오고 있어요"
        review -> "토리: 확인이 필요해요"
        normalized == "success" -> "모아: 완료했어요"
        normalized == "celebrate" -> "토리: 한 번 더 생각한 마음을 응원해요"
        normalized == "empty" -> "햄스터 모아가 쉬고 있어요"
        else -> pose.description
    }
    Box(modifier.size(120.dp).clearAndSetSemantics {
        contentDescription = announcement
        if (normalized == "loading") progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }, contentAlignment = Alignment.Center) {
        Mascot(pose, Modifier.fillMaxSize(), size = 120.dp,
            breathing = normalized == "idle" || normalized == "empty")
        if (normalized == "loading" || review) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(5.dp).size(30.dp)
                    .background(AppColors.Surface, CircleShape).padding(3.dp)
                    .background(AppColors.BlueTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (normalized == "loading") {
                    if (reducedMotion) AppIcon("clock", Modifier.size(18.dp), AppColors.Blue)
                    else CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), color = AppColors.Blue,
                        strokeWidth = 2.dp, trackColor = AppColors.Border
                    )
                } else {
                    AppIcon("info", Modifier.size(18.dp), AppColors.Blue)
                }
            }
        }
    }
}

/** A thumbnail failure is local presentation state and cannot alter a saved product. */
@Composable
fun ProductImage(item: WishItem, modifier: Modifier = Modifier) {
    val url = item.imageUrl?.takeIf { !item.imageHidden && it.isNotBlank() }
    var bitmap by remember(url) { mutableStateOf(url?.let(ProductThumbnailLoader::cached)) }
    LaunchedEffect(url) {
        if (url != null && bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { ProductThumbnailLoader.load(url) }
        }
    }
    Box(
        modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.Soft),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                loaded.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
            )
        } else if (item.isSample && !item.imageHidden) {
            ProductArtwork(item.artwork, Modifier.fillMaxSize())
        } else {
            AppIcon("box", Modifier.size(28.dp), AppColors.Muted)
        }
    }
}

/** Small in-memory, bounded HTTPS loader. No cookies, credentials, disk storage or product mutation. */
private object ProductThumbnailLoader {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_EDGE = 768
    private val permits = Semaphore(3)
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String): Bitmap? = permits.withPermit {
        cached(url)?.let { return@withPermit it }
        val bitmap = runCatching { fetch(url) }.getOrNull()
        if (bitmap != null) cache.put(url, bitmap)
        bitmap
    }

    private fun publicHttps(raw: String): URI? {
        if (raw.length > 4096) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return null
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return null
        val addresses = InetAddress.getAllByName(host)
        if (addresses.isEmpty() || addresses.any {
                it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                    it.isSiteLocalAddress || it.isMulticastAddress ||
                    (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc)
            }) return null
        return uri
    }

    private fun fetch(raw: String): Bitmap? {
        var address = publicHttps(raw) ?: return null
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        repeat(4) {
            if (SystemClock.elapsedRealtime() > deadline) return null
            val connection = address.toURL().openConnection() as? HttpURLConnection ?: return null
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.useCaches = false
                connection.setRequestProperty("Accept", "image/*")
                connection.setRequestProperty("Cookie", "")
                connection.setRequestProperty("Authorization", "")
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: return null
                        address = publicHttps(address.resolve(location).toString()) ?: return null
                    }
                    200 -> {
                        if (connection.contentLengthLong > MAX_BYTES) return null
                        if (!connection.contentType.orEmpty().startsWith("image/", ignoreCase = true)) return null
                        val output = ByteArrayOutputStream()
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                if (SystemClock.elapsedRealtime() > deadline) return null
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > MAX_BYTES) return null
                                output.write(buffer, 0, count)
                            }
                        }
                        return decode(output.toByteArray())
                    }
                    else -> return null
                }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..16_384 || bounds.outHeight !in 1..16_384) return null
        if (bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
