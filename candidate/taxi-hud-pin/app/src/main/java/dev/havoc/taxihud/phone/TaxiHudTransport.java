package dev.havoc.taxihud.phone;

import dev.havoc.taxihud.phone.state.RideSnapshot;

public interface TaxiHudTransport {
    TaxiHudTransport NO_OP = new TaxiHudTransport() {
        @Override
        public void sendRideState(RideSnapshot snapshot, boolean wakeDisplay) {
        }

        @Override
        public void sendHide(String sessionId, long revision) {
        }

        @Override
        public void sendCountdown(String sessionId, long revision, long deadlineEpochMs) {
        }
    };

    /**
     * Sends the complete visible ride snapshot. Implementations must include the active
     * countdown when {@link RideSnapshot#countdownEndsAtEpochMs} is in the future.
     */
    void sendRideState(RideSnapshot snapshot, boolean wakeDisplay);

    void sendHide(String sessionId, long revision);

    void sendCountdown(String sessionId, long revision, long deadlineEpochMs);
}
