package dev.havoc.taxihud.phone.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class PortableBackupCodecTest {
    @Test
    public void roundTripsEncryptedSettings() {
        char[] password = "portable-secret".toCharArray();
        String encoded = PortableBackupCodec.encrypt(
                TaxiSettingsBackup.APP_ID, "{\"setting\":true}", password);

        assertEquals("{\"setting\":true}", PortableBackupCodec.decrypt(
                TaxiSettingsBackup.APP_ID, encoded, password));
    }

    @Test
    public void rejectsWrongPasswordAndDifferentApp() {
        String encoded = PortableBackupCodec.encrypt(
                TaxiSettingsBackup.APP_ID, "{}", "portable-secret".toCharArray());
        assertRejected(() -> PortableBackupCodec.decrypt(
                TaxiSettingsBackup.APP_ID, encoded, "different-secret".toCharArray()));
        assertRejected(() -> PortableBackupCodec.decrypt(
                "another.app", encoded, "portable-secret".toCharArray()));
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Expected invalid backup to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
