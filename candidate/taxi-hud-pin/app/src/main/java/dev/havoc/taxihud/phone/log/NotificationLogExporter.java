package dev.havoc.taxihud.phone.log;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.havoc.rokid.plugin.taxihudpin.R;
import dev.havoc.taxihud.phone.TaxiLocale;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NotificationLogExporter {
    private static final String AUTHORITY_SUFFIX = ".fileprovider";
    private NotificationLogExporter() { }

    public static void share(Context context) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File exportFile = writeExport(context.getCacheDir(), timestamp,
                NotificationLogBuffer.toJsonl(new NotificationLogStore(context).entries()));
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + AUTHORITY_SUFFIX, exportFile);
        context.startActivity(createChooserIntent(context, uri));
    }
    static Intent createChooserIntent(Context context, Uri uri) {
        Intent share = new Intent(Intent.ACTION_SEND).setType("application/x-ndjson")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(
                        share,
                        TaxiLocale.localized(context).getString(R.string.export_log_chooser))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    static File writeExport(File cacheDirectory, String timestamp, String jsonl) {
        File directory = new File(cacheDirectory, "exports");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create log export directory");
        }
        File file = new File(directory, "Taxi-HUD-adapter-log-" + timestamp + ".jsonl");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(jsonl);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write notification adapter log", exception);
        }
        return file;
    }
    public static int clearCachedExports(File cacheDirectory) {
        File directory = new File(cacheDirectory, "exports");
        File[] files = directory.listFiles((ignored, name) ->
                name.startsWith("Taxi-HUD-adapter-log-") && name.endsWith(".jsonl"));
        if (files == null) return 0;
        int deleted = 0;
        for (File file : files) if (file.delete()) deleted++;
        return deleted;
    }
}
