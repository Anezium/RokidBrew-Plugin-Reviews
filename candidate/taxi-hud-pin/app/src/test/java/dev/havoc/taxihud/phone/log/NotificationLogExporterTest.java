package dev.havoc.taxihud.phone.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import android.content.Intent;
import android.net.Uri;
import org.robolectric.RuntimeEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 35)
public final class NotificationLogExporterTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Test public void clearsOnlyAdapterExports() throws Exception {
        File exports = new File(temporaryFolder.getRoot(), "exports");
        assertTrue(exports.mkdirs());
        File log = new File(exports, "Taxi-HUD-adapter-log-1.jsonl");
        File keep = new File(exports, "keep.txt");
        assertTrue(log.createNewFile()); assertTrue(keep.createNewFile());
        assertEquals(1, NotificationLogExporter.clearCachedExports(temporaryFolder.getRoot()));
        assertFalse(log.exists()); assertTrue(keep.exists());
    }
    @Test public void chooserSharesNdjsonUri() {
        Uri uri = Uri.parse("content://pkg.fileprovider/exports/log.jsonl");
        Intent chooser = NotificationLogExporter.createChooserIntent(
                RuntimeEnvironment.getApplication(), uri);
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
    }
    @Test public void writesUtf8GenericFilename() throws Exception {
        File exported = NotificationLogExporter.writeExport(
                temporaryFolder.getRoot(), "20260731", "{\"text\":\"такси\"}");
        assertEquals("Taxi-HUD-adapter-log-20260731.jsonl", exported.getName());
        assertEquals("{\"text\":\"такси\"}", new String(
                Files.readAllBytes(exported.toPath()), StandardCharsets.UTF_8));
    }
}
