package dev.havoc.taxihud.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import dev.havoc.taxihud.phone.config.AdapterRepository;
import dev.havoc.taxihud.phone.log.NotificationLogStore;
import dev.havoc.taxihud.phone.parse.NotificationAdapterEngine;
import dev.havoc.taxihud.phone.parse.TaxiUpdate;
import dev.havoc.taxihud.phone.state.RideSnapshot;
import dev.havoc.taxihud.phone.state.RideStateMachine;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaxiCoordinatorTest {
    private Context context;
    private RideStateStore store;
    private RecordingPublisher publisher;
    private RecordingTransport transport;
    private RecordingCountdownScheduler scheduler;
    private NotificationLogStore logStore;
    private RideHistoryStore historyStore;
    private MutableClock clock;
    private TaxiCoordinator coordinator;
    private List<String> effectOrder;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        store = new RideStateStore(context);
        store.clear();
        effectOrder = new ArrayList<>();
        publisher = new RecordingPublisher(context, store, effectOrder);
        transport = new RecordingTransport(store, effectOrder);
        scheduler = new RecordingCountdownScheduler(context, store, effectOrder);
        logStore = new NotificationLogStore(context);
        logStore.clear();
        historyStore = new RideHistoryStore(context);
        historyStore.clear();
        clock = new MutableClock(1_000L);
        Executor directExecutor = Runnable::run;
        coordinator = new TaxiCoordinator(
                store,
                new RideStateMachine(),
                publisher,
                transport,
                scheduler,
                new NotificationAdapterEngine(),
                new AdapterRepository(context),
                logStore,
                historyStore,
                clock,
                directExecutor);
    }

    @Test
    public void recordsDistinctRideSessionsForLauncherHistory() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onTaxiUpdate(TaxiUpdate.active("В222ВВ777", "Чёрный", "Geely", "4"));
        coordinator.onTaxiUpdate(TaxiUpdate.active("Е333ЕЕ777", "Зелёный", "Haval", "3"));

        long currentGeneration = store.read().sessionGeneration;
        List<RideHistoryEntry> previous = historyStore.previous(currentGeneration, 2);

        assertEquals(2, previous.size());
        assertEquals("В 222 ВВ ⁷⁷⁷", previous.get(0).plateLine);
        assertEquals("А 111 АА ⁷⁷⁷", previous.get(1).plateLine);
    }

    @Test
    public void sameRideEtaUpdateShowsAndSendsLatestPersistedState() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "", "", "3"));

        assertEquals(2, publisher.shown.size());
        assertEquals("3", publisher.shown.get(1).arrivalMinutes);
        assertEquals(2, transport.rideStates.size());
        assertEquals("3", transport.rideStates.get(1).arrivalMinutes);
        assertEquals(store.read(), publisher.storedAtLastShow);
        assertEquals(store.read(), transport.storedAtLastRideState);
        assertEquals(List.of(true, false), transport.wakeRequests);
    }

    @Test
    public void waitingTransitionWakesOnceAndLaterWaitingUpdateDoesNot() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onTaxiUpdate(TaxiUpdate.waiting("2"));
        coordinator.onTaxiUpdate(TaxiUpdate.waiting("3"));

        assertEquals(List.of(true, true, false), transport.wakeRequests);
    }

    @Test
    public void manualResendUsesStoredVisibleRideAndForcesWakeWithoutRepublishingPhoneCard() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        publisher.shown.clear();
        transport.rideStates.clear();
        transport.wakeRequests.clear();

        assertTrue(coordinator.resendCurrentToGlasses().toCompletableFuture().join());

        assertTrue(publisher.shown.isEmpty());
        assertEquals(1, transport.rideStates.size());
        assertEquals(store.read(), transport.rideStates.get(0));
        assertEquals(List.of(true), transport.wakeRequests);
    }

    @Test
    public void manualResendRejectsEmptyOrDismissedRide() {
        assertFalse(coordinator.resendCurrentToGlasses().toCompletableFuture().join());

        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onManualDismiss();

        assertFalse(coordinator.resendCurrentToGlasses().toCompletableFuture().join());
    }

    @Test
    public void manualDismissCancelsAndSendsHideAfterPersistingDismissal() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        String sessionId = store.read().sessionId();

        coordinator.onManualDismiss();

        assertEquals(1, publisher.cancelCount);
        assertEquals(sessionId, transport.hiddenSessions.get(0));
        assertTrue(store.read().dismissed);
        assertTrue(publisher.storedAtCancel.dismissed);
        assertTrue(transport.storedAtLastHide.dismissed);
    }

    @Test
    public void repeatedManualDismissRetriesNotificationAndPinCleanup() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onManualDismiss();
        RideSnapshot firstDismissal = store.read();

        coordinator.onManualDismiss();

        assertEquals(firstDismissal, store.read());
        assertEquals(2, publisher.cancelCount);
        assertEquals(2, transport.hiddenSessions.size());
        assertEquals(firstDismissal.sessionId(), transport.hiddenSessions.get(1));
    }

    @Test
    public void startedHidesPlatePinAndKeepsTripTrackedForMenu() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onTaxiUpdate(TaxiUpdate.lifecycle(TaxiUpdate.Kind.STARTED));

        assertEquals(1, publisher.shown.size());
        assertEquals(1, publisher.cancelCount);
        assertEquals(1, transport.hiddenSessions.size());
        assertTrue(transport.countdowns.isEmpty());
        assertTrue(scheduler.scheduled.isEmpty());
        assertTrue(store.read().tripInProgress);
        assertFalse(store.read().visible);
    }

    @Test
    public void cancellationEndsTrackingAndKeepsTripPinHidden() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onTaxiUpdate(TaxiUpdate.lifecycle(TaxiUpdate.Kind.STARTED));
        publisher.shown.clear();

        coordinator.onTaxiUpdate(TaxiUpdate.lifecycle(TaxiUpdate.Kind.CANCELLED));

        assertTrue(scheduler.cancelCount >= 1);
        assertTrue(transport.countdowns.isEmpty());
        assertTrue(publisher.shown.isEmpty());
        assertEquals(2, publisher.cancelCount);
        assertFalse(store.read().visible);
        assertFalse(store.read().tripInProgress);
        assertTrue(store.read().ended);
        assertEquals(0L, store.read().countdownEndsAtEpochMs);
    }

    @Test
    public void tapShowsTripPinAndSwipeHidesOnlyThePin() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onTaxiUpdate(TaxiUpdate.tripStarted(20L * 60_000L));
        publisher.shown.clear();
        transport.rideStates.clear();

        coordinator.onTripPinRequested();

        assertEquals(1, publisher.shown.size());
        assertEquals(1, transport.rideStates.size());
        assertTrue(store.read().visible);

        coordinator.onManualDismiss();

        assertEquals(2, publisher.cancelCount);
        assertEquals("А111АА777", store.read().plate);
        assertTrue(store.read().tripInProgress);
        assertFalse(store.read().visible);
        assertFalse(store.read().dismissed);
    }

    @Test
    public void restoreRepublishesOnlyUndismissedRide() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        publisher.shown.clear();
        transport.rideStates.clear();
        transport.wakeRequests.clear();

        coordinator.restore();

        assertEquals(1, publisher.shown.size());
        assertEquals(1, transport.rideStates.size());
        assertEquals(List.of(false), transport.wakeRequests);

        coordinator.onManualDismiss();
        publisher.shown.clear();
        transport.rideStates.clear();
        coordinator.restore();

        assertTrue(publisher.shown.isEmpty());
        assertTrue(transport.rideStates.isEmpty());
        assertFalse(store.read().visible);
        assertEquals(2, publisher.cancelCount);
        assertEquals(2, transport.hiddenSessions.size());
    }

    @Test
    public void restoreWithFutureCountdownPublishesOneFullSnapshotAndSchedulesOnce() {
        RideSnapshot counting = new RideSnapshot(
                "А111АА777", "Синий", "Demo Car", "5", false, "",
                1L, 2L, true, false, 0L, false, 8_000L);
        store.write(counting);
        effectOrder.clear();

        coordinator.restore();

        assertEquals(1, publisher.shown.size());
        assertEquals(counting, publisher.shown.get(0));
        assertEquals(1, transport.rideStates.size());
        assertEquals(counting, transport.rideStates.get(0));
        assertEquals(1, scheduler.scheduled.size());
        assertTrue(transport.countdowns.isEmpty());
        assertEquals(List.of("ride_state", "countdown"), transport.semanticMessages);
        assertEquals(
                List.of("show", "ride_state", "schedule"),
                effectOrder);
    }

    @Test
    public void removalLogsReasonWithoutHidingPhoneOrGlasses() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        publisher.cancelCount = 0;
        transport.hiddenSessions.clear();

        coordinator.onNotificationRemoved(
                2_000L,
                "ru.yandex.taxi",
                "ride-key",
                8,
                "title",
                "text",
                "big",
                Collections.singletonList("line"));

        assertEquals(0, publisher.cancelCount);
        assertTrue(transport.hiddenSessions.isEmpty());
        assertEquals(1, logStore.entries().size());
        assertEquals("removed", logStore.entries().get(0).eventType);
        assertEquals(8, logStore.entries().get(0).removalReason);
        assertEquals("LOG_ONLY", logStore.entries().get(0).decision);
    }

    @Test
    public void postedLogContainsStructuredParserResult() {
        coordinator.onNotificationPosted(
                2_000L,
                "ru.yandex.taxi",
                "ride-key",
                "Яндекс Go",
                "3 мин и приедет Синий Demo Car А 111 АА 777",
                "",
                Collections.emptyList());

        dev.havoc.taxihud.phone.log.NotificationParserResult result =
                logStore.entries().get(0).parserResult;
        assertEquals("ACTIVE", result.status);
        assertEquals("А111АА777", result.plate);
        assertEquals("Синий", result.color);
        assertEquals("Demo Car", result.makeModel);
        assertEquals("3", result.arrivalMinutes);
        assertEquals("", result.waitingMinutes);
    }

    @Test
    public void completeWaitingNotificationReplacesSuppressedPreviousCarEndToEnd() {
        coordinator.onTaxiUpdate(TaxiUpdate.active("А111АА777", "Синий", "Demo Car", "5"));
        coordinator.onManualDismiss();
        publisher.shown.clear();
        transport.rideStates.clear();

        coordinator.onNotificationPosted(
                3_000L,
                "ru.yandex.taxi",
                "waiting-key",
                "Водитель ожидает",
                "Черный Geely Coolray А 123 ВС 78. Водитель ожидает 2 мин",
                "",
                Collections.emptyList());

        assertEquals("А123ВС78", store.read().plate);
        assertEquals("Черный Geely Coolray", store.read().vehicleLine());
        assertEquals("Ожидает: 2 мин", store.read().statusLine());
        assertFalse(store.read().dismissed);
        assertEquals(1, publisher.shown.size());
        assertEquals(1, transport.rideStates.size());
    }

    private static final class RecordingPublisher extends TaxiHudNotificationPublisher {
        final RideStateStore store;
        final List<String> effectOrder;
        final List<RideSnapshot> shown = new ArrayList<>();
        int cancelCount;
        RideSnapshot storedAtLastShow;
        RideSnapshot storedAtCancel;

        RecordingPublisher(Context context, RideStateStore store, List<String> effectOrder) {
            super(context);
            this.store = store;
            this.effectOrder = effectOrder;
        }

        @Override
        public void show(RideSnapshot snapshot) {
            storedAtLastShow = store.read();
            shown.add(snapshot);
            effectOrder.add("show");
        }

        @Override
        public void cancel() {
            storedAtCancel = store.read();
            cancelCount++;
            effectOrder.add("cancel");
        }
    }

    private static final class RecordingTransport implements TaxiHudTransport {
        final RideStateStore store;
        final List<String> effectOrder;
        final List<RideSnapshot> rideStates = new ArrayList<>();
        final List<Boolean> wakeRequests = new ArrayList<>();
        final List<String> hiddenSessions = new ArrayList<>();
        final List<CountdownCall> countdowns = new ArrayList<>();
        final List<String> semanticMessages = new ArrayList<>();
        RideSnapshot storedAtLastRideState;
        RideSnapshot storedAtLastHide;
        RideSnapshot storedAtLastCountdown;

        RecordingTransport(RideStateStore store, List<String> effectOrder) {
            this.store = store;
            this.effectOrder = effectOrder;
        }

        @Override
        public void sendRideState(RideSnapshot snapshot, boolean wakeDisplay) {
            storedAtLastRideState = store.read();
            rideStates.add(snapshot);
            wakeRequests.add(wakeDisplay);
            effectOrder.add("ride_state");
            semanticMessages.add("ride_state");
            if (snapshot.countdownEndsAtEpochMs > 0L) {
                semanticMessages.add("countdown");
            }
        }

        @Override
        public void sendHide(String sessionId, long revision) {
            storedAtLastHide = store.read();
            hiddenSessions.add(sessionId);
            effectOrder.add("hide");
        }

        @Override
        public void sendCountdown(String sessionId, long revision, long deadlineEpochMs) {
            storedAtLastCountdown = store.read();
            countdowns.add(new CountdownCall(sessionId, revision, deadlineEpochMs));
            effectOrder.add("countdown");
            semanticMessages.add("countdown");
        }
    }

    private static final class RecordingCountdownScheduler extends CountdownScheduler {
        final RideStateStore store;
        final List<String> effectOrder;
        final List<CountdownCall> scheduled = new ArrayList<>();
        RideSnapshot storedAtSchedule;
        int cancelCount;

        RecordingCountdownScheduler(
                Context context, RideStateStore store, List<String> effectOrder) {
            super(context);
            this.store = store;
            this.effectOrder = effectOrder;
        }

        @Override
        public void schedule(String sessionId, long revision, long deadlineEpochMs) {
            storedAtSchedule = store.read();
            scheduled.add(new CountdownCall(sessionId, revision, deadlineEpochMs));
            effectOrder.add("schedule");
        }

        @Override
        public void cancel() {
            cancelCount++;
        }
    }

    private static final class CountdownCall {
        final String sessionId;
        final long revision;
        final long deadlineEpochMs;

        CountdownCall(String sessionId, long revision, long deadlineEpochMs) {
            this.sessionId = sessionId;
            this.revision = revision;
            this.deadlineEpochMs = deadlineEpochMs;
        }
    }

    private static final class MutableClock implements LongSupplier {
        long now;

        MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
