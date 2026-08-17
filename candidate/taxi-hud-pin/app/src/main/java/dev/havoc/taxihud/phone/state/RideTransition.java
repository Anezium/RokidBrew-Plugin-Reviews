package dev.havoc.taxihud.phone.state;

public final class RideTransition {
    public enum Command {
        NONE,
        SHOW_OR_UPDATE,
        HIDE,
        START_COUNTDOWN,
        CLEAR_COUNTDOWN
    }

    public final RideSnapshot snapshot;
    public final Command command;

    public RideTransition(RideSnapshot snapshot, Command command) {
        this.snapshot = snapshot;
        this.command = command;
    }
}
