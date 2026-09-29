package org.ralphthon.haruman_ralphthon.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ProductApiSettingsStatus(val configured: Boolean, val baseUrl: String? = null, val readError: Boolean = false)

sealed interface ProductApiSettingsResult {
    data object Success : ProductApiSettingsResult
    data class Failure(val message: String) : ProductApiSettingsResult
}

/** No built-in endpoint or key. The URL and token share one authenticated, non-backed-up envelope. */
class ProductApiSettings(context: Context, namespace: String = "production") {
    private val namespaceHash = MessageDigest.getInstance("SHA-256")
        .digest(namespace.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    internal val encryptedFile = File(context.applicationContext.noBackupFilesDir, "product-api/$namespaceHash.bin")
    internal val keyAlias = "haruman.product-api.$namespaceHash"
    private val atomic = AtomicFile(encryptedFile)
    private val aad = "haruman-product-api-v1:$namespaceHash".toByteArray(Charsets.UTF_8)

    val baseUrl: String? get() = status().baseUrl

    fun read(): ProductApiConfig? = synchronized(LOCK) {
        readPayload()?.let { ProductApiConfig(it.baseUrl, "Bearer ${it.token}", 5_000, 60_000) }
    }

    fun status(): ProductApiSettingsStatus = synchronized(LOCK) {
        val payload = readPayload()
        ProductApiSettingsStatus(payload != null, payload?.baseUrl,
            readError = payload == null && (encryptedFile.exists() || File(encryptedFile.path + ".bak").exists()))
    }

    /** A blank replacement token keeps the existing token. It never means unauthenticated access. */
    fun save(baseUrl: String, token: String): ProductApiSettingsResult = synchronized(LOCK) {
        val normalized = normalizeProductApiBaseUrl(baseUrl)
            ?: return@synchronized ProductApiSettingsResult.Failure("HTTPS 서버 주소만 입력해 주세요. 경로 없이 주소와 포트만 넣어 주세요")
        if (token.any { it.isISOControl() }) return@synchronized ProductApiSettingsResult.Failure("토큰에는 줄바꿈이나 제어 문자를 넣을 수 없어요")
        val entered = token.trim().let { if (it.startsWith("Bearer ", ignoreCase = true)) it.substring(7).trim() else it }
        val existing = if (token.isBlank()) readPayload() else null
        if (token.isBlank() && existing != null && existing.baseUrl != normalized) {
            return@synchronized ProductApiSettingsResult.Failure("서버 주소를 바꿀 때는 토큰도 다시 입력해 주세요")
        }
        val effective = if (token.isBlank()) existing?.token else entered
        if (effective.isNullOrBlank()) return@synchronized ProductApiSettingsResult.Failure("연결할 토큰을 입력해 주세요")
        if (effective.length > MAX_TOKEN || effective.any { it.code !in 33..126 }) {
            return@synchronized ProductApiSettingsResult.Failure("토큰 형식을 확인해 주세요. 공백 없이 입력해 주세요")
        }
        var stream: java.io.FileOutputStream? = null
        try {
            val plain = JSONObject().put("version", 1).put("baseUrl", normalized).put("token", effective)
                .toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(create = true))
            cipher.updateAAD(aad)
            val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
            val bytes = ByteArrayOutputStream().apply {
                DataOutputStream(this).use { out ->
                    out.writeInt(MAGIC); out.writeByte(cipher.iv.size); out.write(cipher.iv)
                    out.writeInt(encrypted.size); out.write(encrypted)
                }
            }.toByteArray()
            if (!encryptedFile.parentFile!!.isDirectory && !encryptedFile.parentFile!!.mkdirs()) {
                return@synchronized ProductApiSettingsResult.Failure("연결 정보를 저장할 공간을 만들지 못했어요")
            }
            stream = atomic.startWrite()
            stream.write(bytes)
            atomic.finishWrite(stream)
            ProductApiSettingsResult.Success
        } catch (_: Exception) {
            runCatching { atomic.failWrite(stream) }
            ProductApiSettingsResult.Failure("연결 정보를 안전하게 저장하지 못했어요. 다시 시도해 주세요")
        }
    }

    fun clear(): ProductApiSettingsResult = synchronized(LOCK) {
        try {
            atomic.delete()
            val keys = keyStore()
            if (keys.containsAlias(keyAlias)) keys.deleteEntry(keyAlias)
            if (encryptedFile.exists() || File(encryptedFile.path + ".bak").exists() || File(encryptedFile.path + ".new").exists()) {
                ProductApiSettingsResult.Failure("연결 정보를 아직 지우지 못했어요. 다시 시도해 주세요")
            } else ProductApiSettingsResult.Success
        } catch (_: Exception) {
            ProductApiSettingsResult.Failure("연결 정보를 아직 지우지 못했어요. 다시 시도해 주세요")
        }
    }

    /** Avoid a generated toString that could include a credential. */
    private class Payload(val baseUrl: String, val token: String)

    private fun readPayload(): Payload? = runCatching {
        val bytes = atomic.openRead().use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                check(buffer.size() + count <= MAX_FILE)
                buffer.write(chunk, 0, count)
            }
            buffer.toByteArray()
        }
        val data = DataInputStream(ByteArrayInputStream(bytes))
        check(data.readInt() == MAGIC)
        val ivLength = data.readUnsignedByte()
        check(ivLength == 12)
        val iv = ByteArray(ivLength).also(data::readFully)
        val encryptedLength = data.readInt()
        check(encryptedLength in 16..MAX_FILE && data.available() == encryptedLength)
        val encrypted = ByteArray(encryptedLength).also(data::readFully)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(create = false), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(encrypted)
        try {
            val json = JSONObject(plain.toString(Charsets.UTF_8))
            check(json.getInt("version") == 1)
            val base = normalizeProductApiBaseUrl(json.getString("baseUrl")) ?: error("Invalid stored configuration")
            val token = json.getString("token")
            check(token.isNotBlank() && token.length <= MAX_TOKEN && token.all { it.code in 33..126 })
            Payload(base, token)
        } finally { plain.fill(0) }
    }.getOrNull()

    private fun secretKey(create: Boolean): SecretKey {
        val keys = keyStore()
        (keys.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        check(create) { "No configuration key" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build())
        }.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        val LOCK = Any()
        const val MAGIC = 0x48524131
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAX_TOKEN = 4096
        const val MAX_FILE = 32 * 1024
    }
}

/** Root-only HTTPS authority, including the documented 8443 endpoint. Never accepts credentials. */
fun normalizeProductApiBaseUrl(value: String): String? = runCatching {
    if (value.length > 2048 || value.any { it.isISOControl() }) return@runCatching null
    val uri = URI(value.trim())
    if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/") ||
        uri.port !in listOf(-1, 443, 8443) || uri.rawAuthority.endsWith(":")) return@runCatching null
    URI("https", null, uri.host.lowercase(Locale.ROOT), if (uri.port == 443) -1 else uri.port, null, null, null).toASCIIString()
}.getOrNull()
