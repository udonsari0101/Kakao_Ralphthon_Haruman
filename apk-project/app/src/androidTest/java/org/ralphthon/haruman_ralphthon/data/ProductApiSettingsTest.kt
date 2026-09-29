package org.ralphthon.haruman_ralphthon.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

/** Every test file and AndroidKeyStore alias uses a UUID namespace, never production. */
@RunWith(AndroidJUnit4::class)
class ProductApiSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "product-api-test-${UUID.randomUUID()}"
    private val settings = ProductApiSettings(context, namespace)
    private val extra = ProductApiSettings(context, "$namespace-other")
    @After fun cleanOwnCredentials() { settings.clear(); extra.clear() }

    @Test fun encryptedRoundTripUsesNonBackupStorageAndNeverPersistsUrlOrTokenInPlaintext() {
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, TOKEN))
        val restored = ProductApiSettings(context, namespace)
        assertTrue(restored.status().configured)
        assertEquals(BASE, restored.baseUrl)
        val config = restored.read()!!
        assertEquals(BASE, config.baseUrl)
        assertEquals("Bearer $TOKEN", config.authorizationHeader)
        assertEquals(5_000, config.connectTimeoutMillis)
        assertEquals(60_000, config.readTimeoutMillis)
        assertTrue(settings.encryptedFile.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + java.io.File.separator))
        val persisted = settings.encryptedFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(persisted.contains(BASE)); assertFalse(persisted.contains(TOKEN))
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertNull(keys.getKey(settings.keyAlias, null).encoded)
    }

    @Test fun blankTokenOnlyKeepsCredentialForTheSameNormalizedAuthority() {
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, TOKEN))
        assertEquals(ProductApiSettingsResult.Success, settings.save("https://API.EXAMPLE.TEST:8443/", ""))
        assertEquals("Bearer $TOKEN", settings.read()!!.authorizationHeader)
        assertTrue(settings.save("https://different.example.test:8443", "") is ProductApiSettingsResult.Failure)
        assertTrue(settings.save("https://api.example.test", "") is ProductApiSettingsResult.Failure)
        assertEquals(BASE, settings.baseUrl)
        assertEquals("Bearer $TOKEN", settings.read()!!.authorizationHeader)
        assertEquals(ProductApiSettingsResult.Success, settings.save("https://different.example.test:8443", "explicit-new-fixture-token"))
        assertEquals("https://different.example.test:8443", settings.baseUrl)
    }

    @Test fun invalidUrlsAndHeaderControlsCannotOverwriteExistingConfiguration() {
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, TOKEN))
        listOf("http://api.example.test", "https://user:pass@api.example.test", "$BASE/v1/products/extract",
            "$BASE?token=value", "$BASE#fragment", "https://api.example.test:8080", "https://api.example.test:", "$BASE\n").forEach { bad ->
            assertTrue(settings.save(bad, TOKEN) is ProductApiSettingsResult.Failure)
        }
        listOf("a\r\nAuthorization: injected", "abc\n", "abc\t", "two words", "한글").forEach { bad ->
            assertTrue(settings.save(BASE, bad) is ProductApiSettingsResult.Failure)
        }
        assertEquals(BASE, settings.baseUrl)
        assertEquals("Bearer $TOKEN", settings.read()!!.authorizationHeader)
    }

    @Test fun authenticatedCiphertextRejectsTamperingAndEachSaveUsesFreshIv() {
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, TOKEN))
        val first = settings.encryptedFile.readBytes()
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, TOKEN))
        val second = settings.encryptedFile.readBytes()
        assertFalse(first.contentEquals(second))
        second[second.lastIndex] = (second.last().toInt() xor 1).toByte()
        settings.encryptedFile.writeBytes(second)
        assertNull(settings.read())
        assertTrue(settings.status().readError)
        assertFalse(settings.status().configured)
    }

    @Test fun independentNamespacesNeverShareKeysAndClearRemovesOnlyItsOwnCredential() {
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, TOKEN))
        assertEquals(ProductApiSettingsResult.Success, extra.save("https://other.example.test", "other-fixture-token"))
        assertNotEquals(settings.keyAlias, extra.keyAlias)
        assertNotEquals(settings.encryptedFile, extra.encryptedFile)
        assertEquals(ProductApiSettingsResult.Success, settings.clear())
        assertNull(ProductApiSettings(context, namespace).read())
        assertFalse(settings.encryptedFile.exists())
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(keys.containsAlias(settings.keyAlias))
        assertEquals("Bearer other-fixture-token", extra.read()!!.authorizationHeader)
    }

    @Test fun newSetupRequiresTokenAndOptionalBearerPrefixIsNormalizedOnce() {
        assertTrue(settings.save(BASE, "") is ProductApiSettingsResult.Failure)
        assertFalse(settings.encryptedFile.exists())
        assertEquals(ProductApiSettingsResult.Success, settings.save(BASE, "Bearer $TOKEN"))
        assertEquals("Bearer $TOKEN", settings.read()!!.authorizationHeader)
        assertEquals("https://api.example.test", normalizeProductApiBaseUrl(" https://API.EXAMPLE.TEST:443/ "))
    }

    companion object {
        private const val BASE = "https://api.example.test:8443"
        private const val TOKEN = "dummy-instrumentation-token-only-0123456789"
    }
}
