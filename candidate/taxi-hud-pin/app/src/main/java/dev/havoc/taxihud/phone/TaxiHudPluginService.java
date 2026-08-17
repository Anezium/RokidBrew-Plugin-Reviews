package dev.havoc.taxihud.phone;

import android.view.KeyEvent;

import com.anezium.rokidbus.client.PluginRegistrationResult;
import com.anezium.rokidbus.client.plugin.NexusCard;
import com.anezium.rokidbus.client.plugin.NexusImage;
import com.anezium.rokidbus.client.plugin.NexusPluginService;
import com.anezium.rokidbus.client.plugin.NexusSdkResult;
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession;
import com.anezium.rokidbus.shared.plugin.NexusInputEvent;

import dev.havoc.taxihud.phone.state.RideSnapshot;

/**
 * The sole glasses-facing endpoint. The service talks only to the local Nexus phone hub;
 * Rokid Nexus owns the one physical link and the renderer installed on the glasses.
 */
public final class TaxiHudPluginService extends NexusPluginService {
    private static final String SURFACE_ID = "ride";
    private static final int HISTORY_LIMIT = 5;
    private static volatile TaxiHudPluginService openInstance;
    private NexusSurfaceSession surface;
    private NexusStatusStore statusStore;
    private RideSnapshot pendingSnapshot;
    private boolean nexusOpen;

    @Override
    public void onCreate() {
        super.onCreate();
        statusStore = new NexusStatusStore(this);
        surface = nexusSurfaceSession(SURFACE_ID);
    }

    static void renderCurrentRideIfOpen() {
        TaxiHudPluginService instance = openInstance;
        if (instance != null) {
            instance.renderCurrentState(false);
        }
    }

    static void clearPinAndRenderHistoryIfOpen() {
        TaxiHudPluginService instance = openInstance;
        if (instance != null) {
            instance.clearPinAndRenderHistory();
        }
    }

    @Override
    protected void onNexusOpen() {
        nexusOpen = true;
        openInstance = this;
        RideSnapshot snapshot = currentRide();
        RideHistoryStore history = history();
        history.record(snapshot, System.currentTimeMillis());
        if (hasCurrentRide(snapshot)) {
            pendingSnapshot = snapshot;
            render(snapshot, true);
        } else {
            renderEmpty(history.recent(HISTORY_LIMIT), true);
        }
    }

    @Override
    protected void onNexusClose() {
        if (openInstance == this) {
            openInstance = null;
        }
        nexusOpen = false;
        surface = null;
    }

    @Override
    protected void onNexusInput(NexusInputEvent event) {
        if (!isClearInput(event)) {
            return;
        }
        RideSnapshot snapshot = currentRide();
        if (snapshot != null && snapshot.tripInProgress && !snapshot.ended) {
            TaxiCoordinator.get(this).onTripPinRequested();
        } else if (isVisible(snapshot)) {
            TaxiCoordinator.get(this).onManualDismiss();
        }
    }

    static boolean isClearInput(NexusInputEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
    }

    @Override
    protected void onNexusRegistrationState(int result) {
        statusStore.setRegistrationState(result);
        if (result == PluginRegistrationResult.APPROVED && nexusOpen) {
            renderCurrentState(true);
        }
    }

    @Override
    protected void onNexusLinkState(int state) {
        statusStore.setLinkState(state);
        if (state != 0 && nexusOpen) {
            renderCurrentState(false);
        }
    }

    @Override
    public void onDestroy() {
        if (openInstance == this) {
            openInstance = null;
        }
        if (statusStore != null) {
            statusStore.setLinkState(0);
        }
        super.onDestroy();
    }

    private void renderCurrentRide(boolean forceShow) {
        RideSnapshot snapshot = currentRide();
        if (!hasCurrentRide(snapshot)) {
            renderEmpty(history().recent(HISTORY_LIMIT), forceShow || !nexusOpen);
            return;
        }
        pendingSnapshot = snapshot;
        render(snapshot, forceShow || !nexusOpen);
    }

    private void render(RideSnapshot snapshot, boolean show) {
        long now = System.currentTimeMillis();
        java.util.List<RideHistoryEntry> previous =
                history().previous(snapshot.sessionGeneration, HISTORY_LIMIT - 1);
        TaxiHudImageFrame frame = TaxiHudImageFactory.ride(this, snapshot, previous, now);
        NexusSdkResult result = show ? show(frame) : update(frame);
        if (result == NexusSdkResult.CAPABILITY_NOT_AVAILABLE
                || result == NexusSdkResult.INVALID_PAYLOAD) {
            NexusCard fallback = TaxiHudCardFactory.ride(this, snapshot, previous, now);
            result = show ? show(fallback) : update(fallback);
        }
        if (result == NexusSdkResult.NOT_REGISTERED) {
            pendingSnapshot = snapshot;
        }
    }

    private void renderEmpty(java.util.List<RideHistoryEntry> recent, boolean show) {
        // Opening a history-only card is also a reconciliation point. If the phone hub
        // survived longer than this plugin process, it may still own the last canonical pin.
        if (getNexusClient() != null) {
            getNexusClient().hidePin();
        }
        TaxiHudImageFrame frame = TaxiHudImageFactory.empty(
                this, recent, System.currentTimeMillis());
        NexusSdkResult result = show ? show(frame) : update(frame);
        if (result == NexusSdkResult.CAPABILITY_NOT_AVAILABLE
                || result == NexusSdkResult.INVALID_PAYLOAD) {
            NexusCard fallback = TaxiHudCardFactory.empty(this, recent);
            if (show) {
                show(fallback);
            } else {
                update(fallback);
            }
        }
    }

    private void renderCurrentState(boolean show) {
        RideSnapshot snapshot = currentRide();
        if (hasCurrentRide(snapshot)) {
            pendingSnapshot = snapshot;
            render(snapshot, show);
        } else {
            pendingSnapshot = null;
            renderEmpty(history().recent(HISTORY_LIMIT), show);
        }
    }

    private NexusSdkResult show(NexusCard card) {
        NexusSurfaceSession current = ensureSurface();
        return current == null ? NexusSdkResult.NOT_REGISTERED : current.showCard(card);
    }

    private NexusSdkResult update(NexusCard card) {
        NexusSurfaceSession current = ensureSurface();
        return current == null ? NexusSdkResult.NOT_REGISTERED : current.updateCard(card);
    }

    private NexusSdkResult show(TaxiHudImageFrame frame) {
        NexusSurfaceSession current = ensureSurface();
        NexusImage image = frame.image;
        return current == null
                ? NexusSdkResult.NOT_REGISTERED
                : current.showImage(image, frame.bytes);
    }

    private NexusSdkResult update(TaxiHudImageFrame frame) {
        NexusSurfaceSession current = ensureSurface();
        NexusImage image = frame.image;
        return current == null
                ? NexusSdkResult.NOT_REGISTERED
                : current.updateImage(image, frame.bytes);
    }

    private void clearPinAndRenderHistory() {
        pendingSnapshot = null;
        renderEmpty(history().recent(HISTORY_LIMIT), false);
    }

    private NexusSurfaceSession ensureSurface() {
        if (surface == null) {
            surface = nexusSurfaceSession(SURFACE_ID);
        }
        return surface;
    }

    private RideSnapshot currentRide() {
        RideSnapshot stored = new RideStateStore(this).read();
        if (!stored.plate.isEmpty() || stored.revision > 0L) {
            return stored;
        }
        return pendingSnapshot;
    }

    private RideHistoryStore history() {
        return new RideHistoryStore(this);
    }

    private static boolean hasCurrentRide(RideSnapshot snapshot) {
        return snapshot != null
                && !snapshot.ended
                && !snapshot.plate.isEmpty()
                && (snapshot.tripInProgress || isVisible(snapshot));
    }

    private static boolean isVisible(RideSnapshot snapshot) {
        return snapshot != null
                && snapshot.visible
                && !snapshot.dismissed
                && !snapshot.ended
                && !snapshot.plate.isEmpty();
    }
}
