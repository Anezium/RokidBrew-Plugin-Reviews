package com.havoc.rokidbus.plugin.otps

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import com.anezium.rokidbus.client.ui.BusTheme
import com.anezium.rokidbus.client.ui.NexusUi
import java.util.Locale

class OtpSettingsActivity : Activity() {
    private val settings by lazy { OtpSettings(this) }
    private val history by lazy { OtpHistoryStore(this) }
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NexusUi.BG
        window.navigationBarColor = NexusUi.BG
        content = NexusUi.contentColumn(this)
        val root = NexusUi.fixedRoot(this).apply {
            addView(
                NexusUi.pluginHeader(
                    this@OtpSettingsActivity,
                    R.drawable.nexus_glyph_otps,
                    "OTPs",
                    "Verification codes on your glasses",
                ),
                NexusUi.block(),
            )
            addView(
                NexusUi.screen(this@OtpSettingsActivity, content),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        content.addView(NexusUi.sectionRow(this, "Access"), NexusUi.block())
        content.addView(BusTheme.gap(this, 10))
        content.addView(notificationAccessCard(), NexusUi.block())
        content.addView(BusTheme.gap(this, 8))
        content.addView(
            switchCard(
                title = "OTP alerts",
                subtitle = "Stop detection and glasses alerts",
                checked = settings.enabled(),
            ) { enabled ->
                settings.setEnabled(enabled)
                OtpRuntimeControl.settingsChanged()
            },
            NexusUi.block(),
        )

        content.addView(BusTheme.gap(this, 24))
        content.addView(NexusUi.sectionRow(this, "Alert"), NexusUi.block())
        content.addView(BusTheme.gap(this, 10))
        content.addView(
            switchCard(
                title = "Auto close",
                subtitle = if (settings.autoClose()) "Close after the selected duration" else "Close only from the glasses",
                checked = settings.autoClose(),
            ) { enabled -> settings.setAutoClose(enabled); render() },
            NexusUi.block(),
        )
        if (settings.autoClose()) {
            content.addView(BusTheme.gap(this, 8))
            content.addView(durationCard(), NexusUi.block())
        }

        content.addView(BusTheme.gap(this, 24))
        content.addView(NexusUi.sectionRow(this, "History"), NexusUi.block())
        content.addView(BusTheme.gap(this, 10))
        content.addView(historyCard(), NexusUi.block())

        content.addView(BusTheme.gap(this, 24))
        content.addView(NexusUi.sectionRow(this, "Plugin"), NexusUi.block())
        content.addView(BusTheme.gap(this, 10))
        content.addView(
            NexusUi.uninstallCard(this, "OTPs") {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
            },
            NexusUi.block(),
        )
    }

    private fun notificationAccessCard(): LinearLayout = NexusUi.card(this).apply {
        val granted = hasNotificationAccess()
        addView(NexusUi.cardTitle(this@OtpSettingsActivity, "Notification access"))
        addView(BusTheme.gap(this@OtpSettingsActivity, 5))
        addView(
            NexusUi.cardBody(
                this@OtpSettingsActivity,
                if (granted) "Granted. OTPs can inspect incoming notifications."
                else "Required to find verification codes in notifications and SMS.",
            ),
        )
        addView(BusTheme.gap(this@OtpSettingsActivity, 10))
        addView(
            NexusUi.outlinePillButton(
                this@OtpSettingsActivity,
                if (granted) "Review access" else "Grant access",
            ).apply {
                setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun switchCard(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit,
    ): LinearLayout = NexusUi.card(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            LinearLayout(this@OtpSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(NexusUi.rowTitle(this@OtpSettingsActivity, title))
                addView(BusTheme.gap(this@OtpSettingsActivity, 4))
                addView(NexusUi.rowSub(this@OtpSettingsActivity, subtitle))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(Switch(this@OtpSettingsActivity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        })
    }

    private fun durationCard(): LinearLayout = NexusUi.card(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            LinearLayout(this@OtpSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(NexusUi.rowTitle(this@OtpSettingsActivity, "Duration"))
                addView(BusTheme.gap(this@OtpSettingsActivity, 4))
                addView(NexusUi.rowSub(this@OtpSettingsActivity, "2–45 seconds"))
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(stepButton("−") { changeDuration(-1) })
        addView(
            NexusUi.rowValue(this@OtpSettingsActivity).apply {
                text = String.format(Locale.US, "%ds", settings.durationSeconds())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(NexusUi.dp(this@OtpSettingsActivity, 48), ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(stepButton("+") { changeDuration(1) })
    }

    private fun historyCard(): LinearLayout = NexusUi.card(this).apply {
        val count = history.snapshot().size
        addView(NexusUi.cardTitle(this@OtpSettingsActivity, "Stored codes"))
        addView(BusTheme.gap(this@OtpSettingsActivity, 5))
        addView(NexusUi.cardBody(this@OtpSettingsActivity, "$count of 10 entries"))
        addView(BusTheme.gap(this@OtpSettingsActivity, 10))
        addView(
            NexusUi.outlinePillButton(this@OtpSettingsActivity, "Clear history").apply {
                isEnabled = count > 0
                alpha = if (count > 0) 1f else 0.45f
                setOnClickListener {
                    history.clear()
                    OtpRuntimeControl.notifyHistoryChanged()
                    render()
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun stepButton(label: String, onClick: () -> Unit): Button =
        NexusUi.textButton(this, label).apply { setOnClickListener { onClick() } }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == packageName }
    }

    private fun changeDuration(delta: Int) {
        settings.setDurationSeconds(settings.durationSeconds() + delta)
        render()
    }
}
