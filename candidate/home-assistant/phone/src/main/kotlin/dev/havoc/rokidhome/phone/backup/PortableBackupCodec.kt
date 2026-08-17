package dev.havoc.rokidhome.phone.backup

import org.json.JSONObject
import java.util.Base64
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object PortableBackupCodec {
    private const val FORMAT = "rokid-plugin-backup"
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val MAX_ENCODED_CHARS = 4 * 1024 * 1024
    private val random = SecureRandom()

    fun encrypt(appId: String, plaintext: String, password: CharArray): String {
        require(password.size >= 8) { "Backup password must contain at least 8 characters" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = derive(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(appId))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("appId", appId)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", ITERATIONS)
            .put("cipher", "AES-256-GCM")
            .put("salt", encode(salt))
            .put("iv", encode(iv))
            .put("ciphertext", encode(ciphertext))
            .toString()
    }

    fun decrypt(appId: String, encoded: String, password: CharArray): String {
        require(encoded.length <= MAX_ENCODED_CHARS) { "Backup file is too large" }
        try {
            val container = JSONObject(encoded)
            require(container.getString("format") == FORMAT) { "Unsupported backup format" }
            require(container.getInt("version") == VERSION) { "Unsupported backup version" }
            require(container.getString("appId") == appId) { "Backup belongs to another app" }
            require(container.getString("kdf") == "PBKDF2WithHmacSHA256") {
                "Unsupported backup key derivation"
            }
            require(container.getString("cipher") == "AES-256-GCM") {
                "Unsupported backup cipher"
            }
            val iterations = container.getInt("iterations")
            require(iterations in 100_000..1_000_000) { "Unsafe backup iteration count" }
            val salt = decode(container.getString("salt"))
            val iv = decode(container.getString("iv"))
            val ciphertext = decode(container.getString("ciphertext"))
            require(salt.size == SALT_BYTES && iv.size == IV_BYTES && ciphertext.size >= 16) {
                "Backup encryption parameters are invalid"
            }
            val key = derive(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(appId))
            return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("Wrong password or damaged backup", error)
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("Backup is malformed", error)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun aad(appId: String) = "$FORMAT|$VERSION|$appId".toByteArray(Charsets.UTF_8)

    private fun encode(value: ByteArray) = Base64.getEncoder().encodeToString(value)

    private fun decode(value: String) = Base64.getDecoder().decode(value)
}
