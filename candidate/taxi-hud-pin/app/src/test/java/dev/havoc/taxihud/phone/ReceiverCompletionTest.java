package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.common.util.concurrent.ListenableFuture;

import dev.havoc.taxihud.phone.config.AdapterRepository;
import dev.havoc.taxihud.phone.log.NotificationLogStore;
import dev.havoc.taxihud.phone.parse.NotificationAdapterEngine;
import dev.havoc.taxihud.phone.state.RideSnapshot;
import dev.havoc.taxihud.phone.state.RideStateMachine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBroadcastPendingResult;
import org.robolectric.shadows.ShadowBroadcastReceiver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ReceiverCompletionTest {
    private Context context;
    private RideStateStore store;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        store = new RideStateStore(context);
        store.clear();
        setCoordinatorSingleton(null);
    }

    @After
    public void tearDown() throws Exception {
        setCoordinatorSingleton(null);
    }

    @Test
    public void dismissReceiverKeepsPendingResultUntilSerializedEffectsComplete() throws Exception {
        store.write(activeSnapshot());
        QueuedExecutor executor = new QueuedExecutor();
        setCoordinatorSingleton(coordinator(executor, false, 1_000L));

        NotificationActionReceiver receiver = new NotificationActionReceiver();
        ListenableFuture<BroadcastReceiver.PendingResult> finished = dispatch(
                receiver,
                new Intent(context, NotificationActionReceiver.class)
                        .setAction(NotificationActionReceiver.ACTION_DISMISSED));

        assertFalse(finished.isDone());
        executor.runNext();
        assertTrue(finished.isDone());
        assertTrue(store.read().dismissed);
    }

    @Test
    public void expiryReceiverKeepsPendingResultUntilSerializedEffectsComplete() throws Exception {
        store.write(countingSnapshot());
        QueuedExecutor executor = new QueuedExecutor();
        setCoordinatorSingleton(coordinator(executor, false, 8_000L));

        CountdownExpiryReceiver receiver = new CountdownExpiryReceiver();
        ListenableFuture<BroadcastReceiver.PendingResult> finished = dispatch(
                receiver,
                new Intent(context, CountdownExpiryReceiver.class)
                        .setAction(CountdownExpiryReceiver.ACTION_EXPIRED));

        assertFalse(finished.isDone());
        executor.runNext();
        assertTrue(finished.isDone());
        assertTrue(store.read().plate.isEmpty());
    }

    @Test
    public void dismissReceiverFinishesPendingResultWhenEffectThrows() throws Exception {
        store.write(activeSnapshot());
        QueuedExecutor executor = new QueuedExecutor();
        setCoordinatorSingleton(coordinator(executor, true, 1_000L));

        ListenableFuture<BroadcastReceiver.PendingResult> finished = dispatch(
                new NotificationActionReceiver(),
                new Intent(context, NotificationActionReceiver.class)
                        .setAction(NotificationActionReceiver.ACTION_DISMISSED));

        executor.runNext();
        assertTrue(finished.isDone());
    }

    @Test
    public void expiryReceiverFinishesPendingResultWhenEffectThrows() throws Exception {
        store.write(countingSnapshot());
        QueuedExecutor executor = new QueuedExecutor();
        setCoordinatorSingleton(coordinator(executor, true, 8_000L));

        ListenableFuture<BroadcastReceiver.PendingResult> finished = dispatch(
                new CountdownExpiryReceiver(),
                new Intent(context, CountdownExpiryReceiver.class)
                        .setAction(CountdownExpiryReceiver.ACTION_EXPIRED));

        executor.runNext();
        assertTrue(finished.isDone());
    }

    private ListenableFuture<BroadcastReceiver.PendingResult> dispatch(
            BroadcastReceiver receiver, Intent intent) throws Exception {
        Method createPendingResult = ShadowBroadcastPendingResult.class.getDeclaredMethod(
                "create", int.class, String.class, android.os.Bundle.class, boolean.class);
        createPendingResult.setAccessible(true);
        BroadcastReceiver.PendingResult installedPendingResult =
                (BroadcastReceiver.PendingResult) createPendingResult.invoke(
                        null, 0, null, null, false);
        Field pendingResultField = BroadcastReceiver.class.getDeclaredField("mPendingResult");
        pendingResultField.setAccessible(true);
        pendingResultField.set(receiver, installedPendingResult);

        ShadowBroadcastReceiver shadowReceiver = shadowOf(receiver);
        shadowReceiver.onReceive(context, intent, new AtomicBoolean());
        assertTrue(shadowReceiver.wentAsync());
        BroadcastReceiver.PendingResult pendingResult =
                shadowReceiver.getOriginalPendingResult();
        ShadowBroadcastPendingResult shadowPendingResult = shadowOf(pendingResult);
        return shadowPendingResult.getFuture();
    }

    private TaxiCoordinator coordinator(
            Executor executor, boolean throwOnCancel, long nowEpochMs) {
        TaxiHudNotificationPublisher publisher = new TaxiHudNotificationPublisher(context) {
            @Override
            public void show(RideSnapshot snapshot) {
            }

            @Override
            public void cancel() {
                if (throwOnCancel) {
                    throw new IllegalStateException("effect failed");
                }
            }
        };
        CountdownScheduler scheduler = new CountdownScheduler(context) {
            @Override
            public void schedule(String sessionId, long revision, long deadlineEpochMs) {
            }

            @Override
            public void cancel() {
            }
        };
        return new TaxiCoordinator(
                store,
                new RideStateMachine(),
                publisher,
                TaxiHudTransport.NO_OP,
                scheduler,
                new NotificationAdapterEngine(),
                new AdapterRepository(context),
                new NotificationLogStore(context),
                () -> nowEpochMs,
                executor);
    }

    private static RideSnapshot activeSnapshot() {
        return new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "5", false, "",
                1L, 2L, true, false, 0L, false, 0L);
    }

    private static RideSnapshot countingSnapshot() {
        return new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "5", false, "",
                1L, 3L, true, false, 0L, false, 8_000L);
    }

    private static void setCoordinatorSingleton(TaxiCoordinator coordinator) throws Exception {
        Field field = TaxiCoordinator.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, coordinator);
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            tasks.remove().run();
        }
    }
}
