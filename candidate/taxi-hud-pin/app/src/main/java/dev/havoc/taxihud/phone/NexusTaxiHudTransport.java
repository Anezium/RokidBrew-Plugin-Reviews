package dev.havoc.taxihud.phone;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.anezium.rokidbus.client.HubTarget;
import com.anezium.rokidbus.client.PluginRegistrationResult;
import com.anezium.rokidbus.client.plugin.NexusCard;
import com.anezium.rokidbus.client.plugin.NexusPin;
import com.anezium.rokidbus.client.plugin.NexusPluginCallbacks;
import com.anezium.rokidbus.client.plugin.NexusPluginClient;
import com.anezium.rokidbus.client.plugin.NexusSdkResult;
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession;
import com.anezium.rokidbus.client.plugin.SurfaceModelsKt;
import com.anezium.rokidbus.shared.plugin.NexusInputEvent;

import org.json.JSONObject;

import dev.havoc.taxihud.phone.state.RideSnapshot;

/**
 * Fire-and-forget bridge into the local Nexus phone hub. Nexus remains the only owner of
 * the glasses connection; this client connects only long enough to replace or clear a pin.
 */
public final class NexusTaxiHudTransport implements TaxiHudTransport {
    static final String PLUGIN_ID = "taxi-hud-pin";
    private static final long PIN_RATE_WINDOW_MS = 550L;
    private static final long CONNECT_TIMEOUT_MS = 5_000L;
    private static final long CLIENT_FLUSH_DELAY_MS = 750L;
    private static final String WAKE_SURFACE_ID = "wake-pulse";
    private static volatile NexusTaxiHudTransport instance;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NexusStatusStore statusStore;
    private final Runnable dispatchPendingRunnable = this::dispatchPending;
    private NexusPluginClient pushClient;
    private PendingPin pending;
    private long generation;
    private long lastDispatchElapsedMs = Long.MIN_VALUE;

    public static NexusTaxiHudTransport get(Context context) {
        NexusTaxiHudTransport current = instance;
        if (current == null) {
            synchronized (NexusTaxiHudTransport.class) {
                current = instance;
                if (current == null) {
                    current = new NexusTaxiHudTransport(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    private NexusTaxiHudTransport(Context context) {
        this.context = context;
        statusStore = new NexusStatusStore(context);
    }

    @Override
    public void sendRideState(RideSnapshot snapshot, boolean wakeDisplay) {
        if (snapshot == null) {
            return;
        }
        long nowEpochMs = System.currentTimeMillis();
        NexusPin pin = TaxiHudPinFactory.ride(context, snapshot, nowEpochMs);
        NexusCard wakeCard = wakeDisplay
                ? TaxiHudCardFactory.ride(context, snapshot, nowEpochMs)
                : null;
        mainHandler.post(() -> enqueue(PendingPin.show(pin, wakeCard)));
        mainHandler.post(TaxiHudPluginService::renderCurrentRideIfOpen);
    }

    @Override
    public void sendHide(String sessionId, long revision) {
        mainHandler.post(() -> enqueue(PendingPin.hide()));
        mainHandler.post(TaxiHudPluginService::renderCurrentRideIfOpen);
    }

    @Override
    public void sendCountdown(String sessionId, long revision, long deadlineEpochMs) {
        RideSnapshot snapshot = new RideStateStore(context).read();
        if (snapshot.visible && !snapshot.dismissed && deadlineEpochMs >= 0L) {
            sendRideState(snapshot, false);
        } else {
            sendHide(sessionId, revision);
        }
    }

    public void disconnect() {
        mainHandler.post(this::closeClient);
    }

    private void enqueue(PendingPin command) {
        pending = command;
        mainHandler.removeCallbacks(dispatchPendingRunnable);
        long elapsed = SystemClock.elapsedRealtime();
        long delay = lastDispatchElapsedMs == Long.MIN_VALUE
                ? 0L
                : Math.max(0L, PIN_RATE_WINDOW_MS - (elapsed - lastDispatchElapsedMs));
        mainHandler.postDelayed(dispatchPendingRunnable, delay);
    }

    private void dispatchPending() {
        PendingPin command = pending;
        pending = null;
        if (command == null) {
            return;
        }
        closeClient();
        lastDispatchElapsedMs = SystemClock.elapsedRealtime();
        long requestGeneration = ++generation;
        NexusPluginClient client = NexusPluginClient.Companion.create(
                context,
                PLUGIN_ID,
                new NexusPluginCallbacks() {
                    @Override
                    public void onOpen() {
                    }

                    @Override
                    public void onClose() {
                    }

                    @Override
                    public void onInput(NexusInputEvent event) {
                    }

                    @Override
                    public void onLinkState(int state) {
                        mainHandler.post(() -> statusStore.setLinkState(state));
                    }

                    @Override
                    public void onRegistrationState(int result) {
                        mainHandler.post(() -> finishRegistration(
                                requestGeneration, clientFor(requestGeneration), command, result));
                    }

                    @Override
                    public void onMessage(String path, String id, JSONObject payload) {
                    }
                },
                HubTarget.Companion.getPHONE());
        pushClient = client;
        try {
            client.connect();
            mainHandler.postDelayed(
                    () -> closeIfCurrent(requestGeneration), CONNECT_TIMEOUT_MS);
        } catch (RuntimeException exception) {
            closeIfCurrent(requestGeneration);
        }
    }

    private NexusPluginClient clientFor(long requestGeneration) {
        return requestGeneration == generation ? pushClient : null;
    }

    private void finishRegistration(
            long requestGeneration,
            NexusPluginClient client,
            PendingPin command,
            int result) {
        if (client == null || requestGeneration != generation || client != pushClient) {
            return;
        }
        statusStore.setRegistrationState(result);
        if (result == PluginRegistrationResult.APPROVED) {
            NexusSdkResult sendResult = command.hide
                    ? client.hidePin()
                    : client.showPin(command.pin);
            if (sendResult == NexusSdkResult.SENT) {
                if (!command.hide && command.wakeCard != null) {
                    NexusSurfaceSession wakeSurface =
                            SurfaceModelsKt.surfaceSession(client, WAKE_SURFACE_ID);
                    if (wakeSurface.showCard(command.wakeCard) == NexusSdkResult.SENT) {
                        wakeSurface.hide();
                    }
                }
                mainHandler.postDelayed(
                        () -> closeIfCurrent(requestGeneration), CLIENT_FLUSH_DELAY_MS);
                return;
            }
        }
        closeIfCurrent(requestGeneration);
    }

    private void closeIfCurrent(long requestGeneration) {
        if (requestGeneration == generation) {
            closeClient();
        }
    }

    private void closeClient() {
        NexusPluginClient client = pushClient;
        pushClient = null;
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static final class PendingPin {
        final NexusPin pin;
        final NexusCard wakeCard;
        final boolean hide;

        private PendingPin(NexusPin pin, NexusCard wakeCard, boolean hide) {
            this.pin = pin;
            this.wakeCard = wakeCard;
            this.hide = hide;
        }

        static PendingPin show(NexusPin pin, NexusCard wakeCard) {
            return new PendingPin(pin, wakeCard, false);
        }

        static PendingPin hide() {
            return new PendingPin(null, null, true);
        }
    }
}
