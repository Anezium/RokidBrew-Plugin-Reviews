package dev.havoc.taxihud.phone;

public final class SetupFlow {
    public enum Step {
        NEXUS_APP,
        NOTIFICATION_LISTENER,
        NOTIFICATION_POSTING,
        PLUGIN_APPROVAL,
        READY
    }

    private SetupFlow() {
    }

    public static Step next(
            boolean nexusInstalled,
            boolean notificationListenerEnabled,
            boolean notificationPostingEnabled,
            boolean pluginApproved) {
        if (!nexusInstalled) {
            return Step.NEXUS_APP;
        }
        if (!notificationListenerEnabled) {
            return Step.NOTIFICATION_LISTENER;
        }
        if (!notificationPostingEnabled) {
            return Step.NOTIFICATION_POSTING;
        }
        if (!pluginApproved) {
            return Step.PLUGIN_APPROVAL;
        }
        return Step.READY;
    }
}
