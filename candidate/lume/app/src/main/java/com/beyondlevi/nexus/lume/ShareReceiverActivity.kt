package com.beyondlevi.nexus.lume

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Native Android share target. Accepts shared plain text or PDF/TXT files
 * (single or multiple) from any app and adds them to the Lume library, exactly
 * like the original standalone Lume's share receiver. Headless: no UI, no
 * launcher entry — it extracts on a background thread and finishes.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return finish()
        val store = DocumentStore(applicationContext)
        val resolver = contentResolver
        Thread {
            var added = 0
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (!text.isNullOrBlank()) {
                        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                        if (store.add(title, text, "share") != null) added++
                    } else if (stream != null) {
                        if (addUri(store, stream)) added++
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                    uris.forEach { if (addUri(store, it)) added++ }
                }
            }
            val count = added
            runOnUiThread {
                val msg = when {
                    count <= 0 -> "Nothing to add to Lume"
                    count == 1 -> "Added to Lume"
                    else -> "Added $count documents to Lume"
                }
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    private fun addUri(store: DocumentStore, uri: Uri): Boolean {
        val extracted = runCatching {
            TextExtractor.fromUri(applicationContext, uri, contentResolver.getType(uri))
        }.getOrNull() ?: return false
        if (extracted.text.isBlank()) return false
        return store.add(extracted.title, extracted.text, extracted.source) != null
    }
}
