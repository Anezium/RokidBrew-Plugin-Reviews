package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.havoc.taxihud.phone.parse.TaxiUpdate;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class TestWidgetFlowTest {
    @Test
    public void sendsRandomizedSyntheticActiveRidesWithoutStart() {
        List<TaxiUpdate> updates = new ArrayList<>();
        TestWidgetFlow flow = new TestWidgetFlow(update -> {
            updates.add(update);
            return CompletableFuture.completedFuture(null);
        }, new Random(12345L));

        flow.run().toCompletableFuture().join();
        flow.run().toCompletableFuture().join();

        assertEquals(2, updates.size());
        assertEquals(TaxiUpdate.Kind.ACTIVE, updates.get(0).kind);
        assertTrue(updates.get(0).plate.matches(
                "[АВЕКМНОРСТУХ]\\d{3}[АВЕКМНОРСТУХ]{2}(77|99|197|750|790|799)"));
        assertTrue(Integer.parseInt(updates.get(0).arrivalMinutes) >= 1);
        assertTrue(Integer.parseInt(updates.get(0).arrivalMinutes) <= 12);
        assertTrue(!updates.get(0).color.isEmpty());
        assertTrue(!updates.get(0).makeModel.isEmpty());
        assertTrue(updates.get(0).forceNewSession);
        assertTrue(updates.get(1).forceNewSession);
        assertTrue(!signature(updates.get(0)).equals(signature(updates.get(1))));
    }

    private static String signature(TaxiUpdate update) {
        return update.plate + "|" + update.color + "|" + update.makeModel
                + "|" + update.arrivalMinutes;
    }
}
