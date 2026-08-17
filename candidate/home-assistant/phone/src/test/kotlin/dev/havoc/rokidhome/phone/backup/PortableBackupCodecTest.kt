package dev.havoc.rokidhome.phone.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PortableBackupCodecTest {
    @Test
    fun encryptedBackupRoundTripsWithoutExposingPlaintext() {
        val plaintext = "{\"haToken\":\"secret-token-value\"}"
        val password = "correct horse battery staple".toCharArray()

        val encoded = PortableBackupCodec.encrypt(APP_ID, plaintext, password)

        assertEquals(false, encoded.contains("secret-token-value"))
        assertEquals(plaintext, PortableBackupCodec.decrypt(APP_ID, encoded, password))
    }

    @Test
    fun wrongPasswordAndWrongAppAreRejected() {
        val encoded = PortableBackupCodec.encrypt(
            APP_ID,
            "payload",
            "correct password".toCharArray(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCodec.decrypt(APP_ID, encoded, "wrong password".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCodec.decrypt("another.app", encoded, "correct password".toCharArray())
        }
    }

    private companion object {
        const val APP_ID = "dev.havoc.rokidhome.phone"
    }
}
