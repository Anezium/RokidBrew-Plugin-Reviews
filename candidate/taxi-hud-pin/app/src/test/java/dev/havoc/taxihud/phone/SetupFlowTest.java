package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SetupFlowTest {
    @Test
    public void requestsNexusThenNotificationAccessThenPluginApproval() {
        assertEquals(SetupFlow.Step.NEXUS_APP,
                SetupFlow.next(false, false, false, false));
        assertEquals(SetupFlow.Step.NOTIFICATION_LISTENER,
                SetupFlow.next(true, false, false, false));
        assertEquals(SetupFlow.Step.NOTIFICATION_POSTING,
                SetupFlow.next(true, true, false, false));
        assertEquals(SetupFlow.Step.PLUGIN_APPROVAL,
                SetupFlow.next(true, true, true, false));
        assertEquals(SetupFlow.Step.READY,
                SetupFlow.next(true, true, true, true));
    }
}
