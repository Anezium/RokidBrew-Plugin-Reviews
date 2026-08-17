package dev.havoc.taxihud.phone.state;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public final class RideSnapshotCodec {
    private final Gson gson;

    public RideSnapshotCodec() {
        this(new Gson());
    }

    RideSnapshotCodec(Gson gson) {
        this.gson = gson;
    }

    public String encode(RideSnapshot snapshot) {
        return gson.toJson(snapshot == null ? RideSnapshot.empty() : snapshot);
    }

    public RideSnapshot decode(String json) {
        if (json == null || json.trim().isEmpty()) {
            return RideSnapshot.empty();
        }
        try {
            RideSnapshot decoded = gson.fromJson(json, RideSnapshot.class);
            if (decoded == null
                    || decoded.sessionGeneration < 0L
                    || decoded.revision < 0L
                    || decoded.dismissedAtEpochMs < 0L
                    || decoded.countdownEndsAtEpochMs < 0L
                    || decoded.tripEndsAtEpochMs < 0L) {
                return RideSnapshot.empty();
            }
            return new RideSnapshot(
                    decoded.plate,
                    decoded.color,
                    decoded.makeModel,
                    decoded.arrivalMinutes,
                    decoded.waiting,
                    decoded.waitingMinutes,
                    decoded.sessionGeneration,
                    decoded.revision,
                    decoded.visible,
                    decoded.dismissed,
                    decoded.dismissedAtEpochMs,
                    decoded.ended,
                    decoded.countdownEndsAtEpochMs,
                    decoded.sourceAdapterId,
                    decoded.sourceDisplayName,
                    decoded.sourcePackage,
                    decoded.pinTtlMs,
                    decoded.tripInProgress,
                    decoded.tripEndsAtEpochMs);
        } catch (JsonParseException | IllegalStateException exception) {
            return RideSnapshot.empty();
        }
    }
}
