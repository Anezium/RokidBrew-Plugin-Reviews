package com.beyondlevi.nexus.plugin.agenda

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.anezium.rokidbus.client.ui.BusTheme
import com.anezium.rokidbus.client.ui.NexusPluginIcons
import com.anezium.rokidbus.client.ui.NexusUi

/**
 * The plugin's only phone screen: the hub opens it by explicit component from
 * Plugin access. It is exported without an intent-filter, so it never reaches a
 * launcher — the headless contract.
 *
 * Everything here is built from the NexusUi/BusTheme kit; no XML layouts, no
 * hand-rolled colours. A row action never calls [buildUi] synchronously inside
 * its own click listener (that tears down the view dispatching the click) —
 * rebuilds are posted to the main handler.
 */
class AgendaActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: AgendaPrefs
    private lateinit var repository: CalendarRepository
    private var status: TextView? = null
    private var scroll: ScrollView? = null
    private var pendingScrollY: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AgendaPrefs(this)
        repository = CalendarRepository(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        // The permission may have been granted from Android Settings while we
        // were in the background, and calendars may have synced since.
        rebuild()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode != REQUEST_CALENDAR) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        status?.text = getString(if (granted) R.string.settings_granted else R.string.settings_denied)
        rebuild()
    }

    /**
     * A full rebuild is for structural changes only (the permission was granted,
     * calendars appeared). Toggling a row must NOT come through here: rebuilding
     * throws the screen back to the top, which on a long calendar list means
     * scrolling down again for every single toggle. Flipping a value updates that
     * row's own view in place instead.
     *
     * When a rebuild is unavoidable, the scroll offset is carried across it.
     */
    private fun rebuild() {
        pendingScrollY = scroll?.scrollY ?: 0
        main.post { buildUi() }
    }

    private fun buildUi() {
        window.statusBarColor = NexusUi.BG
        window.navigationBarColor = NexusUi.BG
        val granted = repository.hasAccess()

        val content = NexusUi.contentColumn(this).apply {
            addView(section(R.string.settings_section_access), NexusUi.block())
            addView(BusTheme.gap(this@AgendaActivity, 10))
            addView(accessCard(granted), NexusUi.block())
            addView(BusTheme.gap(this@AgendaActivity, 8))
            addView(statusLine(granted), NexusUi.block())

            addView(BusTheme.gap(this@AgendaActivity, 24))
            addView(section(R.string.settings_section_horizon), NexusUi.block())
            addView(BusTheme.gap(this@AgendaActivity, 10))
            addView(horizonCard(), NexusUi.block())

            addView(BusTheme.gap(this@AgendaActivity, 24))
            addView(section(R.string.settings_section_filters), NexusUi.block())
            addView(BusTheme.gap(this@AgendaActivity, 10))
            addView(
                toggleRow(
                    title = getString(R.string.settings_all_day),
                    sub = null,
                    isOn = { prefs.includeAllDay },
                    onToggle = { prefs.includeAllDay = !prefs.includeAllDay },
                ),
                NexusUi.block(),
            )
            addView(BusTheme.gap(this@AgendaActivity, 8))
            addView(
                toggleRow(
                    title = getString(R.string.settings_hide_declined),
                    sub = null,
                    isOn = { prefs.hideDeclined },
                    onToggle = { prefs.hideDeclined = !prefs.hideDeclined },
                ),
                NexusUi.block(),
            )

            addView(BusTheme.gap(this@AgendaActivity, 24))
            addView(section(R.string.settings_section_calendars), NexusUi.block())
            addView(BusTheme.gap(this@AgendaActivity, 10))
            addCalendarRows(this, granted)

            addView(BusTheme.gap(this@AgendaActivity, 24))
            addView(section(R.string.settings_section_plugin), NexusUi.block())
            addView(BusTheme.gap(this@AgendaActivity, 10))
            addView(uninstallRow(), NexusUi.block())
        }

        val root = NexusUi.fixedRoot(this).apply {
            addView(
                NexusUi.pluginHeader(
                    this@AgendaActivity,
                    NexusPluginIcons.drawableFor("calendar"),
                    getString(R.string.hud_title),
                    getString(R.string.settings_subtitle),
                ),
                NexusUi.block(),
            )
            addView(
                NexusUi.screen(this@AgendaActivity, content).also { scroll = it },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)
        // Restore after layout: scrollTo before the children are measured is a no-op.
        val restoreTo = pendingScrollY
        pendingScrollY = 0
        if (restoreTo > 0) scroll?.post { scroll?.scrollTo(0, restoreTo) }
    }

    private fun section(labelRes: Int, value: String? = null) =
        NexusUi.sectionRow(this, getString(labelRes), value)

    private fun statusLine(granted: Boolean): TextView = NexusUi.statusLine(this).also {
        it.text = getString(if (granted) R.string.settings_granted else R.string.settings_grant_sub)
        status = it
    }

    private fun accessCard(granted: Boolean): LinearLayout = row(
        title = getString(if (granted) R.string.settings_granted else R.string.settings_grant),
        sub = getString(R.string.settings_grant_sub),
        value = getString(if (granted) R.string.settings_on else R.string.settings_off),
    ) {
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CALENDAR)
        } else {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    /** One row that cycles the horizon: a single tap target, no picker dialog. */
    private fun horizonCard(): LinearLayout {
        lateinit var value: TextView
        return row(
            title = getString(R.string.settings_section_horizon),
            sub = null,
            value = horizonSummary(),
            bindValue = { value = it },
        ) {
            val current = HORIZONS.indexOf(prefs.horizonDays)
            prefs.horizonDays = HORIZONS[(current + 1).mod(HORIZONS.size)]
            value.text = horizonSummary()
        }
    }

    private fun horizonSummary(): String =
        if (prefs.horizonDays == 1) {
            getString(R.string.settings_horizon_day)
        } else {
            getString(R.string.settings_horizon_days, prefs.horizonDays)
        }

    /**
     * A row whose value flips in place. [isOn] is read again after every tap, so
     * the row shows the stored state rather than a snapshot taken at build time.
     */
    private fun toggleRow(
        title: String,
        sub: String?,
        isOn: () -> Boolean,
        onToggle: () -> Unit,
    ): LinearLayout {
        lateinit var value: TextView
        val card = row(
            title = title,
            sub = sub,
            value = onOff(isOn()),
            bindValue = { value = it },
        ) {
            onToggle()
            value.text = onOff(isOn())
        }
        return card
    }

    private fun onOff(on: Boolean): String =
        getString(if (on) R.string.settings_on else R.string.settings_off)

    private fun addCalendarRows(column: LinearLayout, granted: Boolean) {
        if (!granted) {
            column.addView(NexusUi.cardBody(this, getString(R.string.settings_grant_sub)), NexusUi.block())
            return
        }
        val calendars = repository.calendars()
        if (calendars.isEmpty()) {
            column.addView(
                NexusUi.cardBody(this, getString(R.string.settings_no_calendars)),
                NexusUi.block(),
            )
            return
        }
        val allIds = calendars.map { it.id }.toSet()
        calendars.forEach { calendar ->
            column.addView(
                toggleRow(
                    title = calendar.displayName.ifBlank { calendar.accountName },
                    sub = calendar.accountName,
                    isOn = { prefs.isCalendarEnabled(calendar.id) },
                    onToggle = {
                        prefs.toggleCalendar(
                            id = calendar.id,
                            enabled = !prefs.isCalendarEnabled(calendar.id),
                            allIds = allIds,
                        )
                    },
                ),
                NexusUi.block(),
            )
            column.addView(BusTheme.gap(this, 8))
        }
        column.addView(
            NexusUi.cardBody(this, getString(R.string.settings_calendars_hint)),
            NexusUi.block(),
        )
    }

    private fun row(
        title: String,
        sub: String?,
        value: String,
        bindValue: ((TextView) -> Unit)? = null,
        onClick: () -> Unit,
    ): LinearLayout = NexusUi.pressableCard(this).apply {
        addView(
            LinearLayout(this@AgendaActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(NexusUi.rowTitle(this@AgendaActivity, title))
                if (!sub.isNullOrBlank()) {
                    addView(BusTheme.gap(this@AgendaActivity, 4))
                    addView(NexusUi.rowSub(this@AgendaActivity, sub))
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            NexusUi.rowValue(this@AgendaActivity).apply {
                text = value
                bindValue?.invoke(this)
            },
        )
        setOnClickListener { onClick() }
    }

    private fun uninstallRow(): LinearLayout =
        NexusUi.uninstallCard(this, getString(R.string.hud_title)) {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        }

    private companion object {
        const val REQUEST_CALENDAR = 1001
        val HORIZONS = listOf(1, 3, 7, 14, 30)
    }
}
