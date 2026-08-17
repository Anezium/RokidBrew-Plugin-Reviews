package com.havoc.rokidbus.plugin.otps

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.anezium.rokidbus.client.ui.BusTheme
import com.anezium.rokidbus.client.ui.NexusUi
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

class OtpSettingsActivity : Activity() {
    private val settings by lazy { OtpSettings(this) }
    private val history by lazy { OtpHistoryStore(this) }
    private lateinit var content: LinearLayout
    private val background = Executors.newSingleThreadExecutor()
    private var pendingExportUri: Uri? = null
    private var pendingImportUri: Uri? = null

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

    override fun onDestroy() {
        background.shutdownNow()
        super.onDestroy()
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
        content.addView(NexusUi.sectionRow(this, "Backup and restore"), NexusUi.block())
        content.addView(BusTheme.gap(this, 10))
        content.addView(backupCard(), NexusUi.block())

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

    @Deprecated("Uses the platform document picker result contract")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        when (requestCode) {
            EXPORT_BACKUP_REQUEST -> {
                pendingExportUri = data.data
                promptExportPassword()
            }
            IMPORT_BACKUP_REQUEST -> {
                pendingImportUri = data.data
                promptImportPassword()
            }
        }
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

    private fun backupCard(): LinearLayout = NexusUi.card(this).apply {
        addView(NexusUi.cardTitle(this@OtpSettingsActivity, "Portable settings"))
        addView(BusTheme.gap(this@OtpSettingsActivity, 5))
        addView(
            NexusUi.cardBody(
                this@OtpSettingsActivity,
                "Password-encrypted export of alert settings. OTP history is intentionally excluded because stored verification codes are short-lived secrets.",
            ),
        )
        addView(BusTheme.gap(this@OtpSettingsActivity, 10))
        addView(
            LinearLayout(this@OtpSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    NexusUi.outlinePillButton(this@OtpSettingsActivity, "Export settings").apply {
                        setOnClickListener { chooseExportFile() }
                    },
                    NexusUi.block(),
                )
                addView(BusTheme.gap(this@OtpSettingsActivity, 8))
                addView(
                    NexusUi.outlinePillButton(this@OtpSettingsActivity, "Import settings").apply {
                        setOnClickListener { chooseImportFile() }
                    },
                    NexusUi.block(),
                )
            },
            NexusUi.block(),
        )
    }

    private fun chooseExportFile() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "otps-settings-$timestamp.rpb"),
            EXPORT_BACKUP_REQUEST,
        )
    }

    private fun chooseImportFile() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json"),
            IMPORT_BACKUP_REQUEST,
        )
    }

    private fun promptExportPassword() {
        val first = passwordField("Backup password")
        val second = passwordField("Repeat password")
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = NexusUi.dp(this@OtpSettingsActivity, 20)
            setPadding(padding, 0, padding, 0)
            addView(first, NexusUi.block())
            addView(BusTheme.gap(this@OtpSettingsActivity, 8))
            addView(second, NexusUi.block())
        }
        AlertDialog.Builder(this)
            .setTitle("Encrypt OTPs settings")
            .setMessage("The password is not stored and cannot be recovered.")
            .setView(fields)
            .setNegativeButton(android.R.string.cancel) { _, _ -> pendingExportUri = null }
            .setPositiveButton("Export") { _, _ ->
                val password = first.text.toString()
                if (password.length < 8 || password != second.text.toString()) {
                    toast("Passwords must match and contain at least 8 characters")
                    pendingExportUri = null
                } else {
                    exportSettings(password.toCharArray())
                }
            }
            .show()
    }

    private fun promptImportPassword() {
        val field = passwordField("Backup password")
        AlertDialog.Builder(this)
            .setTitle("Decrypt OTPs settings")
            .setMessage("Import replaces the current alert settings. OTP history is not changed.")
            .setView(field)
            .setNegativeButton(android.R.string.cancel) { _, _ -> pendingImportUri = null }
            .setPositiveButton("Continue") { _, _ ->
                val password = field.text.toString()
                if (password.length < 8) {
                    toast("Enter the backup password")
                    pendingImportUri = null
                } else {
                    decodeImport(password.toCharArray())
                }
            }
            .show()
    }

    private fun exportSettings(password: CharArray) {
        val uri = pendingExportUri.also { pendingExportUri = null } ?: return
        background.execute {
            val result = runCatching {
                val payload = settings.backup().encode()
                val encoded = PortableBackupCodec.encrypt(OtpSettingsBackup.APP_ID, payload, password)
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                    it.write(encoded)
                } ?: error("Selected file is unavailable")
            }
            password.fill('\u0000')
            runOnUiThread {
                result.fold(
                    onSuccess = { toast("Encrypted OTPs settings exported") },
                    onFailure = { toast(it.message ?: "Export failed") },
                )
            }
        }
    }

    private fun decodeImport(password: CharArray) {
        val uri = pendingImportUri.also { pendingImportUri = null } ?: return
        background.execute {
            val result = runCatching {
                val encoded = readBounded(uri)
                OtpSettingsBackup.decode(
                    PortableBackupCodec.decrypt(OtpSettingsBackup.APP_ID, encoded, password),
                )
            }
            password.fill('\u0000')
            runOnUiThread {
                result.fold(
                    onSuccess = ::confirmImport,
                    onFailure = { toast(it.message ?: "Import failed") },
                )
            }
        }
    }

    private fun confirmImport(backup: OtpSettingsBackup) {
        AlertDialog.Builder(this)
            .setTitle("Restore OTPs settings?")
            .setMessage(
                "Alerts: ${if (backup.enabled) "on" else "off"}\n" +
                    "Auto close: ${if (backup.autoClose) "on" else "off"}\n" +
                    "Duration: ${backup.durationSeconds}s",
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Restore") { _, _ ->
                settings.restore(backup)
                OtpRuntimeControl.settingsChanged()
                render()
                toast("OTPs settings restored")
            }
            .show()
    }

    private fun passwordField(hint: String): EditText = NexusUi.field(this, hint).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun readBounded(uri: Uri): String {
        val output = ByteArrayOutputStream()
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8_192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BACKUP_BYTES) { "Backup file exceeds 1 MB" }
                output.write(buffer, 0, count)
            }
        } ?: error("Selected file is unavailable")
        return output.toString(Charsets.UTF_8.name())
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

    private companion object {
        const val EXPORT_BACKUP_REQUEST = 801
        const val IMPORT_BACKUP_REQUEST = 802
        const val MAX_BACKUP_BYTES = 1024 * 1024
    }
}
