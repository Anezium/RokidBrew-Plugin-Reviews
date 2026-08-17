package dev.havoc.taxihud.phone;

import dev.havoc.taxihud.phone.config.AdapterRepository;
import dev.havoc.taxihud.phone.config.NotificationAdapterConfig;
import dev.havoc.taxihud.phone.log.NotificationLogEvent;
import dev.havoc.taxihud.phone.log.NotificationLogStore;
import dev.havoc.taxihud.phone.log.NotificationParserResult;
import dev.havoc.taxihud.phone.parse.AdapterParseResult;
import dev.havoc.taxihud.phone.parse.NotificationAdapterEngine;
import dev.havoc.taxihud.phone.parse.TaxiUpdate;
import dev.havoc.taxihud.phone.state.RideSnapshot;
import dev.havoc.taxihud.phone.state.RideStateMachine;
import dev.havoc.taxihud.phone.state.RideTransition;
import dev.havoc.taxihud.phone.state.RideWakePolicy;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

public final class TaxiCoordinator {
    private static volatile TaxiCoordinator instance;

    private final RideStateStore store;
    private final RideStateMachine stateMachine;
    private final TaxiHudNotificationPublisher publisher;
    private final TaxiHudTransport transport;
    private final CountdownScheduler countdownScheduler;
    private final NotificationAdapterEngine adapterEngine;
    private final AdapterRepository adapterRepository;
    private final NotificationLogStore logStore;
    private final RideHistoryStore historyStore;
    private final LongSupplier clock;
    private final BooleanSupplier autoTripPin;
    private final TripPinScheduler tripPinScheduler;
    private final Executor executor;

    public static TaxiCoordinator get(Context context) {
        TaxiCoordinator current = instance;
        if (current == null) {
            synchronized (TaxiCoordinator.class) {
                current = instance;
                if (current == null) {
                    current = new TaxiCoordinator(context, NexusTaxiHudTransport.get(context));
                    instance = current;
                }
            }
        }
        return current;
    }

    public TaxiCoordinator(Context context, TaxiHudTransport transport) {
        Context applicationContext = context.getApplicationContext();
        ExecutorService serialExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "taxi-hud-coordinator");
            thread.setDaemon(true);
            return thread;
        });
        this.store = new RideStateStore(applicationContext);
        this.stateMachine = new RideStateMachine();
        this.publisher = new TaxiHudNotificationPublisher(applicationContext);
        this.transport = transport;
        this.countdownScheduler = new CountdownScheduler(applicationContext);
        this.adapterEngine = new NotificationAdapterEngine();
        this.adapterRepository = new AdapterRepository(applicationContext);
        this.logStore = new NotificationLogStore(applicationContext);
        this.historyStore = new RideHistoryStore(applicationContext);
        this.clock = System::currentTimeMillis;
        this.autoTripPin = new TaxiPlatePreferences(applicationContext)::autoTripPin;
        this.tripPinScheduler = new TripPinScheduler(applicationContext);
        this.executor = serialExecutor;
    }

    TaxiCoordinator(
            RideStateStore store,
            RideStateMachine stateMachine,
            TaxiHudNotificationPublisher publisher,
            TaxiHudTransport transport,
            CountdownScheduler countdownScheduler,
            NotificationAdapterEngine adapterEngine,
            AdapterRepository adapterRepository,
            NotificationLogStore logStore,
            LongSupplier clock,
            Executor executor) {
        this(store, stateMachine, publisher, transport, countdownScheduler,
                adapterEngine, adapterRepository, logStore, null, clock, executor);
    }

    TaxiCoordinator(
            RideStateStore store,
            RideStateMachine stateMachine,
            TaxiHudNotificationPublisher publisher,
            TaxiHudTransport transport,
            CountdownScheduler countdownScheduler,
            NotificationAdapterEngine adapterEngine,
            AdapterRepository adapterRepository,
            NotificationLogStore logStore,
            RideHistoryStore historyStore,
            LongSupplier clock,
            Executor executor) {
        this.store = store;
        this.stateMachine = stateMachine;
        this.publisher = publisher;
        this.transport = transport;
        this.countdownScheduler = countdownScheduler;
        this.adapterEngine = adapterEngine;
        this.adapterRepository = adapterRepository;
        this.logStore = logStore;
        this.historyStore = historyStore;
        this.clock = clock;
        this.autoTripPin = () -> false;
        this.tripPinScheduler = null;
        this.executor = executor;
    }

    public CompletionStage<Void> onNotificationPosted(
            long timestampMs,
            String packageName,
            String key,
            String title,
            String text,
            String bigText,
            List<String> textLines) {
        return onNotificationPosted(timestampMs, packageName, key, title, text,
                bigText, textLines, NotificationTiming.NONE);
    }

    public CompletionStage<Void> onNotificationPosted(
            long timestampMs,
            String packageName,
            String key,
            String title,
            String text,
            String bigText,
            List<String> textLines,
            NotificationTiming timing) {
        return onConfiguredNotification(
                timestampMs, packageName, "posted", key,
                title, text, bigText, textLines, timing);
    }

    public CompletionStage<Void> onNotificationSynced(
            long timestampMs,
            String packageName,
            String key,
            String title,
            String text,
            String bigText,
            List<String> textLines) {
        return onNotificationSynced(timestampMs, packageName, key, title, text,
                bigText, textLines, NotificationTiming.NONE);
    }

    public CompletionStage<Void> onNotificationSynced(
            long timestampMs,
            String packageName,
            String key,
            String title,
            String text,
            String bigText,
            List<String> textLines,
            NotificationTiming timing) {
        return onConfiguredNotification(
                timestampMs, packageName, "synced", key,
                title, text, bigText, textLines, timing);
    }

    private CompletionStage<Void> onConfiguredNotification(
            long timestampMs,
            String packageName,
            String eventType,
            String key,
            String title,
            String text,
            String bigText,
            List<String> textLines,
            NotificationTiming timing) {
        List<String> stableLines = textLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(textLines));
        return submit(() -> {
            AdapterParseResult parsed = adapterEngine.parse(
                    packageName, title, text, bigText, stableLines,
                    adapterRepository.enabledAdapters(), timestampMs, timing);
            TaxiUpdate update = parsed.update;
            RideSnapshot previous = store.read();
            RideTransition transition = stateMachine.onTaxiUpdate(
                    previous, update, clock.getAsLong(), autoTripPin.getAsBoolean());
            persist(transition.snapshot);
            logStore.append(new NotificationLogEvent(
                    timestampMs,
                    packageName,
                    parsed.adapter == null ? "" : parsed.adapter.id,
                    parsed.adapter == null ? "" : parsed.adapter.displayName,
                    eventType,
                    value(key),
                    0,
                    value(title),
                    value(text),
                    value(bigText),
                    stableLines,
                    timing,
                    NotificationParserResult.from(update),
                    transition.command.name()));
            apply(previous, transition, true);
        });
    }

    public CompletionStage<Boolean> resendCurrentToGlasses() {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    RideSnapshot snapshot = store.read();
                    boolean sendable = snapshot.visible
                            && !snapshot.dismissed
                            && !snapshot.ended
                            && !snapshot.plate.isEmpty();
                    if (sendable) {
                        transport.sendRideState(snapshot, true);
                    }
                    completion.complete(sendable);
                } catch (Throwable throwable) {
                    completion.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            completion.completeExceptionally(throwable);
        }
        return completion;
    }

    public CompletionStage<Void> onNotificationRemoved(
            long timestampMs,
            String packageName,
            String key,
            int reason,
            String title,
            String text,
            String bigText,
            List<String> textLines) {
        return onNotificationRemoved(timestampMs, packageName, key, reason, title, text,
                bigText, textLines, NotificationTiming.NONE);
    }

    public CompletionStage<Void> onNotificationRemoved(
            long timestampMs,
            String packageName,
            String key,
            int reason,
            String title,
            String text,
            String bigText,
            List<String> textLines,
            NotificationTiming timing) {
        List<String> stableLines = textLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(textLines));
        return submit(() -> {
            NotificationAdapterConfig source = null;
            for (NotificationAdapterConfig adapter : adapterRepository.enabledAdapters()) {
                if (adapter.matchesPackage(packageName)) {
                    source = adapter;
                    break;
                }
            }
            logStore.append(new NotificationLogEvent(
                    timestampMs, packageName,
                    source == null ? "" : source.id,
                    source == null ? "" : source.displayName,
                    "removed", value(key), reason,
                    value(title), value(text), value(bigText), stableLines,
                    timing,
                    NotificationParserResult.empty(), "LOG_ONLY"));
        });
    }

    public CompletionStage<Void> onTaxiUpdate(TaxiUpdate update) {
        return submit(() -> reduce((current, now) ->
                stateMachine.onTaxiUpdate(current, update, now)));
    }

    public CompletionStage<Void> onManualDismiss() {
        return submit(this::dismissNow);
    }

    public CompletionStage<Void> onTripPinRequested() {
        return submit(() -> reduce((current, now) ->
                stateMachine.onTripPinRequested(current)));
    }

    public CompletionStage<Void> onTripPinTick() {
        return submit(() -> {
            RideSnapshot snapshot = store.read();
            if (!snapshot.tripInProgress || !snapshot.visible || snapshot.ended) {
                cancelTripPinRefresh();
                return;
            }
            publisher.show(snapshot);
            transport.sendRideState(snapshot, false);
            scheduleTripPinRefresh(snapshot);
        });
    }

    public CompletionStage<Void> onCountdownExpired() {
        return submit(() -> reduce(stateMachine::onCountdownExpired));
    }

    public CompletionStage<Void> restore() {
        return submit(this::restoreNow);
    }

    private CompletionStage<Void> submit(Runnable event) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    event.run();
                    completion.complete(null);
                } catch (Throwable throwable) {
                    completion.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            completion.completeExceptionally(throwable);
        }
        return completion;
    }

    private void restoreNow() {
        RideSnapshot previous = store.read();
        RideTransition transition = stateMachine.restore(previous, clock.getAsLong());
        persist(transition.snapshot);
        RideSnapshot snapshot = transition.snapshot;
        if (transition.command == RideTransition.Command.START_COUNTDOWN) {
            if (snapshot.dismissed || !snapshot.visible) {
                return;
            }
            publisher.show(snapshot);
            transport.sendRideState(snapshot, false);
            countdownScheduler.schedule(
                    snapshot.sessionId(),
                    snapshot.revision,
                    snapshot.countdownEndsAtEpochMs);
            return;
        }
        if (transition.command == RideTransition.Command.NONE
                && (snapshot.dismissed || snapshot.ended || !snapshot.visible)) {
            countdownScheduler.cancel();
            publisher.cancel();
            String hiddenSession = snapshot.plate.isEmpty()
                    ? previous.sessionId()
                    : snapshot.sessionId();
            transport.sendHide(hiddenSession, snapshot.revision);
            return;
        }
        apply(previous, transition, false);
    }

    private void reduce(Reducer reducer) {
        RideSnapshot previous = store.read();
        RideTransition transition = reducer.reduce(previous, clock.getAsLong());
        persist(transition.snapshot);
        apply(previous, transition, true);
    }

    private void dismissNow() {
        RideSnapshot previous = store.read();
        RideTransition transition = stateMachine.onManualDismiss(previous, clock.getAsLong());
        RideSnapshot snapshot = transition.snapshot;
        persist(snapshot);
        countdownScheduler.cancel();
        cancelTripPinRefresh();
        String hiddenSession = snapshot.plate.isEmpty()
                ? previous.sessionId()
                : snapshot.sessionId();
        try {
            publisher.cancel();
        } finally {
            // Keep manual dismissal idempotent so UI retries repeat the owner-clear request.
            transport.sendHide(hiddenSession, snapshot.revision);
        }
    }

    private void persist(RideSnapshot snapshot) {
        store.write(snapshot);
        if (historyStore != null) {
            historyStore.record(snapshot, clock.getAsLong());
        }
    }

    private void apply(
            RideSnapshot previous, RideTransition transition, boolean allowWake) {
        RideSnapshot snapshot = transition.snapshot;
        if (previous.countdownEndsAtEpochMs > 0L
                && snapshot.countdownEndsAtEpochMs == 0L) {
            countdownScheduler.cancel();
        }
        switch (transition.command) {
            case SHOW_OR_UPDATE:
                publisher.show(snapshot);
                transport.sendRideState(
                        snapshot,
                        allowWake && RideWakePolicy.shouldWake(previous, snapshot));
                scheduleTripPinRefresh(snapshot);
                break;
            case HIDE:
                countdownScheduler.cancel();
                cancelTripPinRefresh();
                String hiddenSession = snapshot.plate.isEmpty()
                        ? previous.sessionId()
                        : snapshot.sessionId();
                try {
                    publisher.cancel();
                } finally {
                    transport.sendHide(hiddenSession, snapshot.revision);
                }
                break;
            case START_COUNTDOWN:
                countdownScheduler.schedule(
                        snapshot.sessionId(),
                        snapshot.revision,
                        snapshot.countdownEndsAtEpochMs);
                transport.sendCountdown(
                        snapshot.sessionId(),
                        snapshot.revision,
                        snapshot.countdownEndsAtEpochMs);
                break;
            case CLEAR_COUNTDOWN:
                transport.sendCountdown(snapshot.sessionId(), snapshot.revision, 0L);
                break;
            case NONE:
                if (!previous.equals(snapshot)) {
                    TaxiHudPluginService.renderCurrentRideIfOpen();
                }
                break;
        }
    }

    private void scheduleTripPinRefresh(RideSnapshot snapshot) {
        if (tripPinScheduler == null) {
            return;
        }
        if (snapshot.tripInProgress && snapshot.visible && !snapshot.ended
                && snapshot.tripEndsAtEpochMs > clock.getAsLong()) {
            tripPinScheduler.schedule(snapshot.tripEndsAtEpochMs);
        } else {
            tripPinScheduler.cancel();
        }
    }

    private void cancelTripPinRefresh() {
        if (tripPinScheduler != null) {
            tripPinScheduler.cancel();
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private interface Reducer {
        RideTransition reduce(RideSnapshot current, long nowEpochMs);
    }
}
