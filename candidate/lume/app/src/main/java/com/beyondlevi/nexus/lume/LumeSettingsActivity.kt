package com.beyondlevi.nexus.lume

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import com.anezium.rokidbus.client.ui.BusTheme
import com.anezium.rokidbus.client.ui.NexusPluginIcons
import com.anezium.rokidbus.client.ui.NexusUi

/**
 * The phone-side library + settings screen — the Nexus equivalent of the
 * original Lume phone host. Import PDF/TXT files or paste text, set the default
 * reading speed and UI language, browse the library with per-document progress,
 * and remove documents. Built only on the NexusUi/BusTheme kit.
 */
class LumeSettingsActivity : Activity() {

    private lateinit var store: DocumentStore
    private lateinit var settings: SettingsStore
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DocumentStore(applicationContext)
        settings = SettingsStore(applicationContext)
        window.statusBarColor = NexusUi.BG
        window.navigationBarColor = NexusUi.BG
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        // Reflect documents added since the screen was built (e.g. via the share target).
        rebuild()
    }

    /**
     * Rebuilds the screen off the current input dispatch. A row action (e.g.
     * Remove) replaces the whole view tree via setContentView; doing that
     * synchronously inside the click destroys the very view handling the click
     * and leaves a blank screen, so defer to the next loop.
     */
    private fun rebuild() {
        ui.post { buildUi() }
    }

    private fun buildUi() {
        store.reload()
        val ctx = this
        val content = NexusUi.contentColumn(ctx).apply {
            addView(
                NexusUi.cardBody(ctx, "Glanceable RSVP reading on Rokid glasses. Add documents here; open Lume from the glasses launcher to read."),
                NexusUi.block(),
            )
            addView(BusTheme.gap(ctx, 20))

            // Reading speed
            addView(NexusUi.sectionRow(ctx, "Reading speed", "${settings.defaultWpm} wpm"), NexusUi.block())
            addView(BusTheme.gap(ctx, 10))
            addView(speedCard(), NexusUi.block())
            addView(BusTheme.gap(ctx, 20))

            // Language
            addView(NexusUi.sectionRow(ctx, "Language", if (settings.language == "pt") "Português" else "English"), NexusUi.block())
            addView(BusTheme.gap(ctx, 10))
            addView(languageCard(), NexusUi.block())
            addView(BusTheme.gap(ctx, 20))

            // Add
            addView(NexusUi.sectionRow(ctx, "Add to library"), NexusUi.block())
            addView(BusTheme.gap(ctx, 10))
            addView(addCard(), NexusUi.block())
            addView(BusTheme.gap(ctx, 20))

            // Library
            addView(NexusUi.sectionRow(ctx, "Library", "${store.documents().size}"), NexusUi.block())
            addView(BusTheme.gap(ctx, 10))
            libraryCards().forEach { addView(it, NexusUi.block()); addView(BusTheme.gap(ctx, 10)) }
            addView(BusTheme.gap(ctx, 14))

            // Plugin (uninstall)
            addView(NexusUi.sectionRow(ctx, "Plugin"), NexusUi.block())
            addView(BusTheme.gap(ctx, 10))
            addView(uninstallRow(), NexusUi.block())
        }
        val root = NexusUi.fixedRoot(ctx).apply {
            addView(
                NexusUi.pluginHeader(
                    ctx,
                    NexusPluginIcons.drawableFor("bookmark"),
                    "Lume",
                    "Glanceable RSVP reader · v${versionName()}",
                ),
                NexusUi.block(),
            )
            addView(
                NexusUi.screen(ctx, content),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)
    }

    private fun speedCard(): LinearLayout {
        val ctx = this
        return NexusUi.card(ctx).apply {
            addView(NexusUi.rowTitle(ctx, "${settings.defaultWpm} wpm"), NexusUi.block())
            addView(NexusUi.rowSub(ctx, "Seeds each reading session (${RsvpEngine.MIN_WPM}-${RsvpEngine.MAX_WPM})."), NexusUi.block())
            addView(BusTheme.gap(ctx, 10))
            addView(
                NexusUi.outlinePillButton(ctx, "Faster +${RsvpEngine.WPM_STEP}").apply {
                    setOnClickListener {
                        settings.defaultWpm = settings.defaultWpm + RsvpEngine.WPM_STEP
                        rebuild()
                    }
                },
                NexusUi.block(),
            )
            addView(BusTheme.gap(ctx, 8))
            addView(
                NexusUi.outlinePillButton(ctx, "Slower -${RsvpEngine.WPM_STEP}").apply {
                    setOnClickListener {
                        settings.defaultWpm = settings.defaultWpm - RsvpEngine.WPM_STEP
                        rebuild()
                    }
                },
                NexusUi.block(),
            )
        }
    }

    private fun languageCard(): LinearLayout {
        val ctx = this
        return NexusUi.card(ctx).apply {
            addView(
                NexusUi.pillButton(ctx, if (settings.language == "pt") "Switch to English" else "Mudar para Português").apply {
                    setOnClickListener {
                        settings.language = if (settings.language == "pt") "en" else "pt"
                        rebuild()
                    }
                },
                NexusUi.block(),
            )
        }
    }

    private fun addCard(): LinearLayout {
        val ctx = this
        val titleField = NexusUi.field(ctx, "Title (optional)")
        val textField = NexusUi.field(ctx, "Paste text to read")
        return NexusUi.card(ctx).apply {
            addView(
                NexusUi.pillButton(ctx, "Import PDF or TXT file").apply {
                    setOnClickListener { pickFile() }
                },
                NexusUi.block(),
            )
            addView(BusTheme.gap(ctx, 12))
            addView(NexusUi.divider(ctx), NexusUi.block())
            addView(BusTheme.gap(ctx, 12))
            addView(titleField, NexusUi.block())
            addView(BusTheme.gap(ctx, 8))
            addView(textField, NexusUi.block())
            addView(BusTheme.gap(ctx, 10))
            addView(
                NexusUi.outlinePillButton(ctx, "Add pasted text").apply {
                    setOnClickListener {
                        val text = textField.text?.toString().orEmpty()
                        if (text.isBlank()) return@setOnClickListener
                        addInBackground(titleField.text?.toString().orEmpty(), text, "share")
                        textField.setText("")
                        titleField.setText("")
                    }
                },
                NexusUi.block(),
            )
        }
    }

    private fun libraryCards(): List<LinearLayout> {
        val ctx = this
        val docs = store.documents()
        if (docs.isEmpty()) {
            return listOf(
                NexusUi.card(ctx).apply {
                    addView(NexusUi.rowSub(ctx, "No documents yet. Import a file or paste text above."), NexusUi.block())
                },
            )
        }
        return docs.map { doc ->
            NexusUi.card(ctx).apply {
                addView(NexusUi.rowTitle(ctx, doc.title), NexusUi.block())
                val pct = if (doc.totalWords > 0) doc.progressWordIndex * 100 / doc.totalWords else 0
                addView(NexusUi.rowSub(ctx, "${doc.source.uppercase()} · ${doc.totalWords} words · $pct% read"), NexusUi.block())
                addView(BusTheme.gap(ctx, 8))
                addView(
                    NexusUi.textButton(ctx, "Remove", danger = true).apply {
                        setOnClickListener {
                            store.delete(doc.id)
                            rebuild()
                        }
                    },
                    NexusUi.block(),
                )
            }
        }
    }

    private fun uninstallRow() = NexusUi.uninstallCard(this, "Lume") {
        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
    }

    /** Real versionName from the package manager — never hardcode it (avoids header drift). */
    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/plain"))
        }
        startActivityForResult(intent, REQUEST_PICK_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        Thread {
            val extracted = runCatching {
                TextExtractor.fromUri(applicationContext, uri, contentResolver.getType(uri))
            }.getOrNull()
            runOnUiThread {
                if (extracted != null && extracted.text.isNotBlank()) {
                    store.add(extracted.title, extracted.text, extracted.source)
                }
                rebuild()
            }
        }.start()
    }

    private fun addInBackground(title: String, text: String, source: String) {
        Thread {
            store.add(title, text, source)
            runOnUiThread { rebuild() }
        }.start()
    }

    private companion object {
        const val REQUEST_PICK_FILE = 1001
    }
}
