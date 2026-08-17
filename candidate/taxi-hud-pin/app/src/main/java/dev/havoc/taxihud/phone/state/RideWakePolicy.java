package dev.havoc.taxihud.phone.state;

public final class RideWakePolicy {
    private RideWakePolicy() {
    }

    public static boolean shouldWake(RideSnapshot previous, RideSnapshot current) {
        if (current == null || !current.visible || current.dismissed) {
            return false;
        }
        if (previous == null || !previous.sessionId().equals(current.sessionId())) {
            return true;
        }
        return !previous.waiting && current.waiting;
    }
}
