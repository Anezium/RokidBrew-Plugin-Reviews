package dev.havoc.taxihud.phone;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class TestWidgetDispatchInstrumentedTest {
    @Test
    public void dispatchesThroughTheProductionCoordinator() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new TestWidgetFlow(target, TaxiCoordinator.get(target)::onTaxiUpdate)
                .run()
                .toCompletableFuture()
                .get(10L, TimeUnit.SECONDS);
        SystemClock.sleep(4_000L);
    }
}
