package com.havoc.rokidbus.plugin.otps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableBackupCodecTest {
    @Test
    fun encryptedSettingsRoundTripWithoutPlaintext() {
        val payload = OtpSettingsBackup(true, false, 17).encode()
        val password = "correct horse battery staple".toCharArray()

        val encoded = PortableBackupCodec.encrypt(OtpSettingsBackup.APP_ID, payload, password)
        val restored = OtpSettingsBackup.decode(
            PortableBackupCodec.decrypt(OtpSettingsBackup.APP_ID, encoded, password),
        )

        assertFalse(encoded.contains("durationSeconds"))
        assertEquals(OtpSettingsBackup(true, false, 17), restored)
    }

    @Test
    fun wrongPasswordAndWrongAppAreRejected() {
        val password = "correct password".toCharArray()
        val encoded = PortableBackupCodec.encrypt(
            OtpSettingsBackup.APP_ID,
            OtpSettingsBackup(true, true, 12).encode(),
            password,
        )
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCodec.decrypt(
                OtpSettingsBackup.APP_ID,
                encoded,
                "wrong password".toCharArray(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCodec.decrypt("another.app", encoded, password)
        }
    }
}
