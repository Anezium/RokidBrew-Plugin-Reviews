package com.beyondlevi.nexus.lume

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Extracts plain text from PDF and TXT content shared with or picked by the app.
 * Ported verbatim from the original Lume; extraction stays fully on-device.
 */
object TextExtractor {
    @Volatile private var pdfBoxReady = false

    // Extraction bounds to keep memory/time predictable on large PDFs.
    private const val MAX_PDF_BYTES = 30L * 1024 * 1024
    private const val MAX_PDF_PAGES = 800

    data class Extracted(val title: String, val text: String, val source: String)

    /** Reads a `content://` (or `file://`) uri as PDF or plain text. */
    fun fromUri(context: Context, uri: Uri, mimeType: String?): Extracted {
        val name = displayName(context, uri)
        val isPdf = mimeType == "application/pdf" ||
            name.lowercase().endsWith(".pdf")
        return if (isPdf) {
            Extracted(stripExtension(name), extractPdf(context, uri), "pdf")
        } else {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                .orEmpty()
            Extracted(stripExtension(name), text, "txt")
        }
    }

    private fun extractPdf(context: Context, uri: Uri): String {
        // Guard against huge PDFs that would OOM the (bundled, older) PdfBox/Bouncy
        // Castle stack: cap the input size and the number of pages we extract.
        val size = sizeOf(context, uri)
        if (size in (MAX_PDF_BYTES + 1)..Long.MAX_VALUE) {
            throw IllegalStateException("PDF too large: $size bytes (max $MAX_PDF_BYTES)")
        }
        if (!pdfBoxReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { document ->
                    val stripper = PDFTextStripper().apply {
                        startPage = 1
                        endPage = document.numberOfPages.coerceAtMost(MAX_PDF_PAGES)
                    }
                    stripper.getText(document)
                }
            }.orEmpty()
        } catch (oom: OutOfMemoryError) {
            // Degrade to "no text" rather than crashing the process.
            ""
        }
    }

    private fun sizeOf(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            }
        }
        return -1L
    }

    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx).orEmpty()
                }
            }
        }
        return uri.lastPathSegment.orEmpty().ifBlank { "Document" }
    }

    private fun stripExtension(name: String): String =
        name.substringBeforeLast('.').ifBlank { name }
}
