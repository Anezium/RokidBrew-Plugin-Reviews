package dev.havoc.taxihud.phone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.anezium.rokidbus.client.PluginRegistrationResult;
import com.anezium.rokidbus.client.ui.BusTheme;
import com.anezium.rokidbus.client.ui.NexusUi;
import com.anezium.rokidbus.shared.LinkStateBits;
import com.havoc.rokid.plugin.taxihudpin.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.havoc.taxihud.phone.config.AdapterBundleValidator;
import dev.havoc.taxihud.phone.config.AdapterRepository;
import dev.havoc.taxihud.phone.config.NotificationAdapterConfig;
import dev.havoc.taxihud.phone.log.NotificationLogExporter;
import dev.havoc.taxihud.phone.log.NotificationLogStore;
import kotlin.Unit;

/** Nexus-native settings screen opened explicitly by Rokid Nexus. */
public final class MainActivity extends Activity {
    private static final String NEXUS_PACKAGE = "com.anezium.rokidbus.phone";
    private static final int IMPORT_ADAPTERS_REQUEST = 41;
    private static final int POST_NOTIFICATIONS_REQUEST = 42;
    private static final String NEXUS_RELEASES =
            "https://github.com/Anezium/Rokid-Nexus/releases";

    private final Map<Integer, TextView> statusValues = new LinkedHashMap<>();
    private final Map<Integer, View> statusDots = new LinkedHashMap<>();
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshStatuses();
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(TaxiLocale.localized(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refreshStatuses();
        requestNotificationPostingPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(NexusStatusStore.ACTION_STATUS);
        ContextCompat.registerReceiver(
                this,
                statusReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        refreshStatuses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(statusReceiver);
        } catch (RuntimeException ignored) {
        }
        super.onStop();
    }

    private void buildUi() {
        windowColors();
        statusValues.clear();
        statusDots.clear();

        LinearLayout content = NexusUi.INSTANCE.contentColumn(this, 22, 18, 24);
        addSection(content, R.string.section_status);
        content.addView(statusCard(), NexusUi.INSTANCE.block());
        addGap(content, 12);
        content.addView(primaryButton(R.string.continue_setup, this::continueSetup),
                NexusUi.INSTANCE.block());

        addSection(content, R.string.section_setup);
        addLanguageSetting(content);
        addAction(content, R.string.open_nexus, R.string.open_nexus_subtitle,
                this::openNexus);
        addAction(content, R.string.open_notification_access,
                R.string.open_notification_access_subtitle,
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addAction(content, R.string.enable_android_notifications,
                R.string.enable_android_notifications_subtitle,
                this::requestNotificationPostingPermission);

        addSection(content, R.string.section_ride_tools);
        addAutoTripPinSetting(content);
        addAction(content, R.string.test_widget, R.string.test_widget_subtitle,
                this::sendTestWidget);
        addAction(content, R.string.sync_notification, R.string.sync_notification_subtitle,
                this::syncNotification);

        addAdapterSettings(content);
        addLogSettings(content);

        addSection(content, R.string.section_plugin);
        content.addView(NexusUi.INSTANCE.uninstallCard(
                this,
                getString(R.string.app_name),
                () -> {
                    startActivity(new Intent(
                            Intent.ACTION_DELETE,
                            Uri.parse("package:" + getPackageName())));
                    return Unit.INSTANCE;
                }), NexusUi.INSTANCE.block());

        LinearLayout root = NexusUi.INSTANCE.fixedRoot(this);
        root.addView(NexusUi.INSTANCE.pluginHeader(
                this,
                R.drawable.nexus_glyph_taxi_plate,
                getString(R.string.app_name),
                getString(R.string.plugin_header_subtitle, shortVersion())),
                NexusUi.INSTANCE.block());
        root.addView(
                NexusUi.INSTANCE.screen(this, content),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f));
        setContentView(root);
    }

    private void windowColors() {
        getWindow().setStatusBarColor(NexusUi.BG);
        getWindow().setNavigationBarColor(NexusUi.BG);
    }

    private LinearLayout statusCard() {
        LinearLayout card = NexusUi.INSTANCE.card(this);
        addStatusRow(card, R.string.status_nexus_app, false);
        addStatusRow(card, R.string.status_nexus_plugin, false);
        addStatusRow(card, R.string.status_nexus_link, false);
        addStatusRow(card, R.string.status_notification_listener, false);
        addStatusRow(card, R.string.status_notification_posting, false);
        addStatusRow(card, R.string.status_package_version, true);
        return card;
    }

    private void addStatusRow(LinearLayout card, int label, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = NexusUi.INSTANCE.dot(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.setMarginEnd(dp(10));
        row.addView(dot, dotParams);

        TextView title = NexusUi.INSTANCE.rowLabel(this, getString(label));
        row.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = NexusUi.INSTANCE.rowValue(this);
        value.setMaxLines(2);
        row.addView(value, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));

        card.addView(row, NexusUi.INSTANCE.block());
        if (!last) {
            card.addView(NexusUi.INSTANCE.divider(this));
        }
        statusDots.put(label, dot);
        statusValues.put(label, value);
    }

    private void addAdapterSettings(LinearLayout content) {
        addSection(content, R.string.adapters_heading);
        AdapterRepository repository = new AdapterRepository(this);
        List<NotificationAdapterConfig> adapters = repository.adapters();
        for (NotificationAdapterConfig adapter : adapters) {
            LinearLayout card = NexusUi.INSTANCE.pressableCard(this);
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(NexusUi.INSTANCE.rowTitle(this, adapter.displayName));
            labels.addView(BusTheme.INSTANCE.gap(this, 4));
            labels.addView(NexusUi.INSTANCE.rowSub(
                    this,
                    getString(
                            R.string.adapter_summary,
                            adapter.imported
                                    ? getString(R.string.adapter_imported)
                                    : getString(R.string.adapter_builtin),
                            String.join(", ", adapter.packages))));
            card.addView(labels, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView state = NexusUi.INSTANCE.metaLabel(
                    this,
                    getString(adapter.enabled ? R.string.adapter_on : R.string.adapter_off),
                    adapter.enabled ? NexusUi.GREEN_DIM : NexusUi.INK3);
            LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            stateParams.setMarginStart(dp(12));
            card.addView(state, stateParams);
            card.setOnClickListener(view -> {
                repository.setEnabled(adapter.id, !adapter.enabled);
                buildUi();
                refreshStatuses();
            });
            content.addView(card, topMargin(8));
        }

        Button importButton = NexusUi.INSTANCE.outlinePillButton(
                this, getString(R.string.import_adapters));
        importButton.setOnClickListener(view -> openAdapterImport());
        content.addView(importButton, topMargin(10));

        Button resetButton = NexusUi.INSTANCE.textButton(
                this, getString(R.string.reset_adapters), true);
        resetButton.setOnClickListener(view -> confirmAdapterReset(repository));
        content.addView(resetButton, topMargin(4));
    }

    private void addLogSettings(LinearLayout content) {
        addSection(content, R.string.section_diagnostics);
        LinearLayout privacy = NexusUi.INSTANCE.card(this);
        privacy.addView(NexusUi.INSTANCE.cardBody(this, getString(R.string.log_privacy_warning)));
        content.addView(privacy, NexusUi.INSTANCE.block());
        addAction(content, R.string.export_notification_log, R.string.export_log_subtitle,
                () -> {
                    try {
                        NotificationLogExporter.share(this);
                    } catch (RuntimeException exception) {
                        Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
                    }
                });
        Button clearButton = NexusUi.INSTANCE.textButton(
                this, getString(R.string.clear_notification_log), true);
        clearButton.setOnClickListener(view -> {
            new NotificationLogStore(this).clear();
            NotificationLogExporter.clearCachedExports(getCacheDir());
            Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show();
        });
        content.addView(clearButton, topMargin(4));
    }

    private void addSection(LinearLayout root, int label) {
        if (root.getChildCount() > 0) {
            addGap(root, 24);
        }
        root.addView(NexusUi.INSTANCE.sectionRow(this, getString(label), null),
                NexusUi.INSTANCE.block());
        addGap(root, 10);
    }

    private void addAction(LinearLayout root, int title, int subtitle, Runnable action) {
        LinearLayout card = NexusUi.INSTANCE.pressableCard(this);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(NexusUi.INSTANCE.rowTitle(this, getString(title)));
        labels.addView(BusTheme.INSTANCE.gap(this, 4));
        labels.addView(NexusUi.INSTANCE.rowSub(this, getString(subtitle)));
        card.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(NexusUi.INSTANCE.chevron(this));
        card.setOnClickListener(view -> action.run());
        root.addView(card, topMargin(8));
    }

    private void addLanguageSetting(LinearLayout root) {
        LinearLayout card = NexusUi.INSTANCE.pressableCard(this);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(NexusUi.INSTANCE.rowTitle(this, getString(R.string.language_title)));
        labels.addView(BusTheme.INSTANCE.gap(this, 4));
        labels.addView(NexusUi.INSTANCE.rowSub(
                this, getString(TaxiLocale.selectedLabel(this))));
        card.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(NexusUi.INSTANCE.chevron(this));
        card.setOnClickListener(view -> showLanguageDialog());
        root.addView(card, topMargin(8));
    }

    private void addAutoTripPinSetting(LinearLayout root) {
        TaxiPlatePreferences preferences = new TaxiPlatePreferences(this);
        boolean enabled = preferences.autoTripPin();
        LinearLayout card = NexusUi.INSTANCE.pressableCard(this);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(NexusUi.INSTANCE.rowTitle(
                this, getString(R.string.auto_trip_pin_title)));
        labels.addView(BusTheme.INSTANCE.gap(this, 4));
        labels.addView(NexusUi.INSTANCE.rowSub(
                this, getString(R.string.auto_trip_pin_subtitle)));
        card.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = NexusUi.INSTANCE.metaLabel(
                this,
                getString(enabled ? R.string.adapter_on : R.string.adapter_off),
                enabled ? NexusUi.GREEN_DIM : NexusUi.INK3);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stateParams.setMarginStart(dp(12));
        card.addView(state, stateParams);
        card.setOnClickListener(view -> {
            preferences.setAutoTripPin(!preferences.autoTripPin());
            buildUi();
            refreshStatuses();
        });
        root.addView(card, topMargin(8));
    }

    private void showLanguageDialog() {
        String[] tags = {"", TaxiLocale.ENGLISH, TaxiLocale.RUSSIAN};
        String selected = TaxiLocale.selectedLanguageTag(this);
        int checked = TaxiLocale.ENGLISH.equals(selected)
                ? 1
                : TaxiLocale.RUSSIAN.equals(selected) ? 2 : 0;
        String[] labels = {
                getString(R.string.language_system),
                getString(R.string.language_english),
                getString(R.string.language_russian)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.language_dialog_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    TaxiLocale.setLanguage(this, tags[which]);
                    if (Build.VERSION.SDK_INT < 33) {
                        recreate();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Button primaryButton(int label, Runnable action) {
        Button button = NexusUi.INSTANCE.pillButton(this, getString(label), false);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void addGap(LinearLayout root, int size) {
        root.addView(BusTheme.INSTANCE.gap(this, size));
    }

    private LinearLayout.LayoutParams topMargin(int top) {
        LinearLayout.LayoutParams params = NexusUi.INSTANCE.block();
        params.topMargin = dp(top);
        return params;
    }

    private int dp(int value) {
        return NexusUi.INSTANCE.dp(this, value);
    }

    private void confirmAdapterReset(AdapterRepository repository) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_adapters)
                .setMessage(R.string.reset_adapters_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset_adapters_confirm, (dialog, which) -> {
                    repository.resetImported();
                    Toast.makeText(this, R.string.adapters_reset_done, Toast.LENGTH_SHORT).show();
                    buildUi();
                    refreshStatuses();
                })
                .show();
    }

    private void sendTestWidget() {
        TaxiCoordinator coordinator = TaxiCoordinator.get(this);
        new TestWidgetFlow(this, coordinator::onTaxiUpdate).run().whenComplete((ignored, error) ->
                runOnUiThread(() -> Toast.makeText(
                        this,
                        error == null ? R.string.test_widget_sent : R.string.export_failed,
                        Toast.LENGTH_SHORT).show()));
    }

    private void syncNotification() {
        TaxiNotificationListenerService.syncActiveConfiguredNotifications()
                .whenComplete((result, error) -> runOnUiThread(() -> Toast.makeText(
                        this,
                        syncResultMessage(result, error),
                        Toast.LENGTH_SHORT).show()));
    }

    private void openAdapterImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent, IMPORT_ADAPTERS_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_ADAPTERS_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        try {
            String json = readBounded(data.getData());
            int count = new AdapterRepository(this).importJson(json);
            Toast.makeText(this, getString(R.string.adapters_imported, count),
                    Toast.LENGTH_LONG).show();
            buildUi();
            refreshStatuses();
        } catch (IOException | IllegalArgumentException exception) {
            Toast.makeText(this, getString(R.string.adapters_import_failed,
                    exception.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private String readBounded(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IOException(getString(R.string.import_file_unavailable));
            }
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > AdapterBundleValidator.MAX_JSON_BYTES) {
                    throw new IOException(getString(R.string.import_file_too_large));
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void continueSetup() {
        NexusStatusStore nexus = new NexusStatusStore(this);
        SetupFlow.Step step = SetupFlow.next(
                nexusInstalled(), notificationListenerEnabled(),
                notificationPostingEnabled(), nexus.isApproved());
        switch (step) {
            case NEXUS_APP:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(NEXUS_RELEASES)));
                break;
            case NOTIFICATION_LISTENER:
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                break;
            case NOTIFICATION_POSTING:
                requestNotificationPostingPermission();
                break;
            case PLUGIN_APPROVAL:
                openNexus();
                Toast.makeText(this, R.string.approve_plugin_in_nexus, Toast.LENGTH_LONG).show();
                break;
            case READY:
                Toast.makeText(this, R.string.setup_ready, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void openNexus() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(NEXUS_PACKAGE);
        if (launch != null) {
            startActivity(launch);
        } else {
            Toast.makeText(this, R.string.nexus_not_installed, Toast.LENGTH_LONG).show();
        }
    }

    private int syncResultMessage(
            TaxiNotificationListenerService.SyncResult result, Throwable error) {
        if (error != null || result == null) {
            return R.string.sync_notification_failed;
        }
        switch (result) {
            case SENT:
                return R.string.sync_notification_sent;
            case LISTENER_UNAVAILABLE:
                return R.string.sync_listener_unavailable;
            case NO_CONFIGURED_NOTIFICATIONS:
                return R.string.sync_no_configured_notifications;
            case NO_CURRENT_RIDE:
            default:
                return R.string.sync_no_current_ride;
        }
    }

    private void refreshStatuses() {
        if (statusValues.isEmpty()) {
            return;
        }
        NexusStatusStore nexus = new NexusStatusStore(this);
        setStatus(R.string.status_nexus_app, nexusInstalled());

        int registration = nexus.registrationState();
        setStatusText(
                R.string.status_nexus_plugin,
                registrationLabel(registration),
                registration == PluginRegistrationResult.APPROVED);

        int link = nexus.linkState();
        setStatusText(
                R.string.status_nexus_link,
                linkLabel(link),
                (link & LinkStateBits.CXR_CONTROL_UP) != 0);

        setStatus(R.string.status_notification_listener, notificationListenerEnabled());
        setStatus(R.string.status_notification_posting, notificationPostingEnabled());
        setStatusText(R.string.status_package_version, packageVersion(), true);
    }

    private String registrationLabel(int state) {
        switch (state) {
            case PluginRegistrationResult.APPROVED:
                return getString(R.string.nexus_plugin_approved);
            case PluginRegistrationResult.DENIED:
                return getString(R.string.nexus_plugin_denied);
            case PluginRegistrationResult.INVALID_DESCRIPTOR:
            case PluginRegistrationResult.IDENTITY_MISMATCH:
            case PluginRegistrationResult.UNSUPPORTED_API:
            case PluginRegistrationResult.REGISTRATION_FAILED:
                return getString(R.string.nexus_plugin_error, state);
            case PluginRegistrationResult.PENDING_USER_APPROVAL:
            default:
                return getString(R.string.nexus_plugin_pending);
        }
    }

    private String linkLabel(int state) {
        if ((state & LinkStateBits.CXR_CONTROL_UP) == 0) {
            return getString(R.string.nexus_link_disconnected);
        }
        if ((state & LinkStateBits.SPP_DATA_UP) != 0) {
            return getString(R.string.nexus_link_data_ready);
        }
        return getString(R.string.nexus_link_control_ready);
    }

    private boolean nexusInstalled() {
        try {
            getPackageManager().getPackageInfo(NEXUS_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private boolean notificationListenerEnabled() {
        String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) {
            return false;
        }
        ComponentName expected = new ComponentName(this, TaxiNotificationListenerService.class);
        for (String value : flat.split(":")) {
            if (expected.equals(ComponentName.unflattenFromString(value))) {
                return true;
            }
        }
        return false;
    }

    private boolean notificationPostingEnabled() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPostingPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationPostingEnabled()) {
            requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS},
                    POST_NOTIFICATIONS_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == POST_NOTIFICATIONS_REQUEST) {
            refreshStatuses();
        }
    }

    private String packageVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException impossible) {
            return getPackageName();
        }
    }

    private String shortVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException impossible) {
            return "—";
        }
    }

    private void setStatus(int label, boolean ready) {
        setStatusText(
                label,
                getString(ready ? R.string.status_enabled : R.string.status_disabled),
                ready);
    }

    private void setStatusText(int label, String value, boolean ready) {
        TextView row = statusValues.get(label);
        View dot = statusDots.get(label);
        if (row != null) {
            row.setText(value);
            row.setTextColor(ready ? NexusUi.GREEN_DIM : NexusUi.INK3);
        }
        if (dot != null) {
            NexusUi.INSTANCE.setDotColor(dot, ready ? NexusUi.GREEN : NexusUi.INK4);
        }
    }
}
