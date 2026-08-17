package dev.havoc.taxihud.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.anezium.rokidbus.client.plugin.NexusImage;
import com.anezium.rokidbus.client.ui.BusTheme;
import com.havoc.rokid.plugin.taxihudpin.R;

import java.io.ByteArrayOutputStream;
import java.util.List;

import dev.havoc.taxihud.phone.state.RideSnapshot;

final class TaxiHudImageFactory {
    static final int WIDTH = 480;
    static final int HEIGHT = 512;
    static final int BUTTON_LEFT = 18;
    static final int BUTTON_TOP = 438;
    static final int BUTTON_RIGHT = WIDTH - 18;
    static final int BUTTON_BOTTOM = HEIGHT - 12;
    private static final int MAX_IMAGE_BYTES = 65_536;

    private TaxiHudImageFactory() {
    }

    static TaxiHudImageFrame ride(
            Context context,
            RideSnapshot snapshot,
            List<RideHistoryEntry> history,
            long nowEpochMs) {
        Bitmap bitmap = renderBitmap(context, snapshot, history, nowEpochMs);
        String contentKey = "taxi-hud-image-"
                + snapshot.sessionGeneration + "-" + snapshot.revision
                + tripMinuteKey(snapshot, nowEpochMs);
        return frame(context, contentKey, bitmap);
    }

    static TaxiHudImageFrame empty(
            Context context, List<RideHistoryEntry> history, long nowEpochMs) {
        Bitmap bitmap = renderEmptyBitmap(context, history);
        long historyVersion = history == null || history.isEmpty()
                ? nowEpochMs
                : history.get(0).updatedAtEpochMs;
        return frame(context, "taxi-hud-history-" + historyVersion, bitmap);
    }

    static Bitmap renderBitmap(
            Context context,
            RideSnapshot snapshot,
            List<RideHistoryEntry> history,
            long nowEpochMs) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        int y = 38;
        y = drawLine(canvas, paint, RideText.plate(context, snapshot),
                30f, BusTheme.phosphor, y, 38);
        y = drawLine(canvas, paint, RideText.vehicle(context, snapshot),
                22f, BusTheme.muted, y, 31);
        y = drawLine(canvas, paint, RideText.status(context, snapshot, nowEpochMs),
                23f, BusTheme.phosphor, y, 34);
        if (!snapshot.sourceDisplayName.isEmpty()) {
            y = drawLine(canvas, paint, snapshot.sourceDisplayName, 18f, BusTheme.dim, y, 28);
        }

        y += 15;
        y = drawLine(canvas, paint, RideText.string(context, R.string.hud_recent_rides),
                18f, BusTheme.dim, y, 30);
        if (history == null || history.isEmpty()) {
            drawLine(canvas, paint, RideText.string(context, R.string.hud_history_empty),
                    19f, BusTheme.muted, y, 29);
        } else {
            int shown = 0;
            for (RideHistoryEntry entry : history) {
                if (entry == null) {
                    continue;
                }
                String line = entry.displayLine();
                if (line.isEmpty()) {
                    continue;
                }
                y = drawLine(canvas, paint, line, 19f, BusTheme.text, y, 31);
                shown++;
                if (shown == 5) {
                    break;
                }
            }
        }

        drawButton(canvas, paint, RideText.string(context, snapshot.tripInProgress
                ? R.string.hud_show_trip_timer
                : R.string.hud_clear_notification));
        return bitmap;
    }

    static Bitmap renderEmptyBitmap(Context context, List<RideHistoryEntry> history) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        int y = 40;
        y = drawLine(
                canvas,
                paint,
                RideText.string(context, R.string.hud_no_active_notification),
                23f,
                BusTheme.phosphor,
                y,
                34);
        y += 15;
        y = drawLine(
                canvas,
                paint,
                RideText.string(context, R.string.hud_recent_rides),
                18f,
                BusTheme.dim,
                y,
                30);
        if (history == null || history.isEmpty()) {
            drawLine(
                    canvas,
                    paint,
                    RideText.string(context, R.string.hud_history_empty),
                    19f,
                    BusTheme.muted,
                    y,
                    31);
            return bitmap;
        }
        int shown = 0;
        for (RideHistoryEntry entry : history) {
            if (entry == null) {
                continue;
            }
            String line = entry.displayLine();
            if (line.isEmpty()) {
                continue;
            }
            y = drawLine(canvas, paint, line, 19f, BusTheme.text, y, 31);
            shown++;
            if (shown == 5) {
                break;
            }
        }
        return bitmap;
    }

    private static TaxiHudImageFrame frame(
            Context context, String contentKey, Bitmap bitmap) {
        EncodedImage encoded = encode(bitmap);
        bitmap.recycle();
        NexusImage image = new NexusImage(
                contentKey,
                encoded.mimeType,
                WIDTH,
                HEIGHT,
                RideText.string(context, R.string.app_name),
                null,
                null,
                false);
        return new TaxiHudImageFrame(image, encoded.bytes);
    }

    private static String tripMinuteKey(RideSnapshot snapshot, long nowEpochMs) {
        if (!snapshot.tripInProgress || snapshot.tripEndsAtEpochMs <= 0L) {
            return "";
        }
        long minutes = Math.max(
                1L, (snapshot.tripEndsAtEpochMs - nowEpochMs + 59_999L) / 60_000L);
        return "-trip-" + minutes;
    }

    private static EncodedImage encode(Bitmap bitmap) {
        byte[] png = compress(bitmap, Bitmap.CompressFormat.PNG, 100);
        if (png.length <= MAX_IMAGE_BYTES) {
            return new EncodedImage("image/png", png);
        }
        for (int quality : new int[] {85, 72, 60}) {
            byte[] jpeg = compress(bitmap, Bitmap.CompressFormat.JPEG, quality);
            if (jpeg.length <= MAX_IMAGE_BYTES) {
                return new EncodedImage("image/jpeg", jpeg);
            }
        }
        throw new IllegalStateException("Taxi Plate image exceeds Nexus limit");
    }

    private static byte[] compress(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(format, quality, output)) {
            throw new IllegalStateException("Taxi Plate image encoding failed");
        }
        return output.toByteArray();
    }

    private static int drawLine(
            Canvas canvas,
            Paint paint,
            String value,
            float textSize,
            int color,
            int baseline,
            int lineHeight) {
        String text = ellipsize(paint, value, textSize, WIDTH - 36f);
        if (!text.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1f);
            paint.setColor(color);
            paint.setTextSize(textSize);
            canvas.drawText(text, 18f, baseline, paint);
            return baseline + lineHeight;
        }
        return baseline;
    }

    private static void drawButton(Canvas canvas, Paint paint, String label) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(BusTheme.phosphor);
        RectF bounds = new RectF(BUTTON_LEFT, BUTTON_TOP, BUTTON_RIGHT, BUTTON_BOTTOM);
        canvas.drawRoundRect(bounds, 4f, 4f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextSize(19f);
        float x = (WIDTH - paint.measureText(label)) / 2f;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, x, y, paint);
    }

    private static String ellipsize(Paint paint, String value, float textSize, float maxWidth) {
        String text = value == null ? "" : value.trim();
        paint.setTextSize(textSize);
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }
        String suffix = "…";
        while (!text.isEmpty() && paint.measureText(text + suffix) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + suffix;
    }

    private static final class EncodedImage {
        final String mimeType;
        final byte[] bytes;

        EncodedImage(String mimeType, byte[] bytes) {
            this.mimeType = mimeType;
            this.bytes = bytes;
        }
    }
}
