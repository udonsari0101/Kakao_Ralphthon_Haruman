package org.ralphthon.haruman_ralphthon.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection

/** All connections are in-memory fakes: no endpoint or credential from local configuration. */
class HttpsProductLinkServiceTest {
    private val productUrl = "https://shop.example.com/products/1?option=blue"
    private fun config(overall: Long = 60_000) =
        ProductApiConfig("https://api.example.com:8443", "Bearer fixture-token", 1_000, 1_000, overall)

    @Test
    fun explicitHttpsPortIsPreservedAndUnsafeConfigurationsRemainDormant() = runBlocking {
        assertEquals("https://api.example.com:8443/v1/products/extract", config().endpoint().toString())
        assertNull(ProductApiConfig("http://api.example.com:8443", "fixture", 100, 100).endpoint())
        assertNull(ProductApiConfig("https://127.0.0.1:8443", "fixture", 100, 100).endpoint())
        assertNull(ProductApiConfig("https://api.example.com:70000", "fixture", 100, 100).endpoint())
        assertNull(ProductApiConfig("https://api.example.com", "fixture\r\nInjected: yes", 100, 100).endpoint())
        val calls = AtomicInteger()
        val absentAuth = HttpsProductLinkService(ProductApiConfig("https://api.example.com", null, 100, 100)) {
            calls.incrementAndGet(); FakeConnection()
        }
        assertEquals(ProductLinkResult.NotConfigured, absentAuth.extract(productUrl))
        assertEquals(0, calls.get())
    }

    @Test
    fun requestPreservesSelectedUrlAndUsesHeadersWithoutFollowingRedirects() = runBlocking {
        val connection = FakeConnection()
        val service = HttpsProductLinkService(config()) { connection }
        val failure = service.extract(productUrl) as ProductLinkResult.Failure
        assertEquals(ProductFailureCode.SERVER_UNAVAILABLE, failure.code)
        assertEquals("POST", connection.requestMethod)
        assertEquals("Bearer fixture-token", connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertEquals(productUrl, org.json.JSONObject(connection.sent.toString("UTF-8")).getString("url"))
    }

    @Test
    fun byteLimitRejectsUntruncatedUnicodeInputBeforeOpeningAConnection() = runBlocking {
        val calls = AtomicInteger()
        val service = HttpsProductLinkService(config()) { calls.incrementAndGet(); FakeConnection() }
        val large = "https://shop.example.com/?q=" + "가".repeat(3_000)
        assertTrue(large.length < InputLimits.URL)
        assertEquals(ProductFailureCode.INVALID_REQUEST, (service.extract(large) as ProductLinkResult.Failure).code)
        assertEquals(0, calls.get())
    }

    @Test
    fun cancellationDisconnectsBlockedIoAndReleasesTheSingleRequestGate() = runBlocking {
        val first = FakeConnection(block = true)
        val calls = AtomicInteger()
        val service = HttpsProductLinkService(config()) { if (calls.incrementAndGet() == 1) first else FakeConnection() }
        val running = async(Dispatchers.Default) { service.extract(productUrl) }
        assertTrue(first.entered.await(2, TimeUnit.SECONDS))
        assertEquals(ProductFailureCode.BUSY, (service.extract(productUrl) as ProductLinkResult.Failure).code)
        assertEquals(1, calls.get())
        running.cancelAndJoin()
        assertTrue(running.isCancelled)
        assertTrue(first.closed.await(2, TimeUnit.SECONDS))
        assertEquals(ProductFailureCode.SERVER_UNAVAILABLE, (service.extract(productUrl) as ProductLinkResult.Failure).code)
        assertEquals(2, calls.get())
    }

    @Test
    fun wholeRequestDeadlineEndsBlockedIoAndDiffersFromServer504() = runBlocking {
        val connection = FakeConnection(block = true)
        val service = HttpsProductLinkService(config(100)) { connection }
        val result = service.extract(productUrl) as ProductLinkResult.Failure
        assertEquals(ProductFailureCode.CLIENT_TIMEOUT, result.code)
        assertTrue(connection.closed.await(2, TimeUnit.SECONDS))
        val server = HttpsProductLinkService(config()) { FakeConnection(status = 504) }
        assertEquals(ProductFailureCode.TIMEOUT, (server.extract(productUrl) as ProductLinkResult.Failure).code)
    }

    @Test
    fun retryAfterBlocksUserRetryLocallyAndTwelveRequestsBoundTheWindow() = runBlocking {
        val calls = AtomicInteger()
        val busy = HttpsProductLinkService(config()) {
            calls.incrementAndGet(); FakeConnection(status = 429, retryAfter = "30")
        }
        assertEquals(30L, (busy.extract(productUrl) as ProductLinkResult.Failure).retryAfterSeconds)
        val again = busy.extract(productUrl) as ProductLinkResult.Failure
        assertEquals(ProductFailureCode.BUSY, again.code)
        assertTrue(again.retryAfterSeconds!! in 1..30)
        assertEquals(1, calls.get())
        val limitedCalls = AtomicInteger()
        val limited = HttpsProductLinkService(config()) { limitedCalls.incrementAndGet(); FakeConnection() }
        repeat(12) { limited.extract(productUrl) }
        val thirteenth = limited.extract(productUrl) as ProductLinkResult.Failure
        assertEquals(ProductFailureCode.BUSY, thirteenth.code)
        assertTrue(thirteenth.retryAfterSeconds!! in 1..60)
        assertEquals(12, limitedCalls.get())
    }

    private class FakeConnection(
        private val status: Int = 502,
        private val retryAfter: String? = null,
        private val block: Boolean = false,
    ) : HttpsURLConnection(URL("https://api.example.com:8443/v1/products/extract")) {
        val sent = ByteArrayOutputStream()
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        private val release = CountDownLatch(1)
        override fun getOutputStream() = sent
        override fun getResponseCode(): Int {
            entered.countDown()
            if (block) {
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) throw IOException("fake blocked response")
                } catch (_: InterruptedException) { throw IOException("fake cancelled") }
                throw IOException("fake disconnected")
            }
            return status
        }
        override fun getHeaderField(name: String?): String? = if (name == "Retry-After") retryAfter else null
        override fun getErrorStream() = ByteArrayInputStream("<html>proxy</html>".toByteArray())
        override fun getInputStream() = getErrorStream()
        override fun connect() = Unit
        override fun disconnect() { closed.countDown(); release.countDown() }
        override fun usingProxy() = false
        override fun getCipherSuite() = "fixture"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
