package com.beyondlevi.nexus.lume

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * The phone-side document library. Each document is stored as extracted plain
 * text under `filesDir/documents/<id>.txt`, with a JSON index carrying the
 * metadata (title, word count, reading progress).
 *
 * Ported from the original Lume `DocumentStore`. In the Nexus port the store is
 * shared between the settings/share Activities (which add/delete documents) and
 * the plugin service (which reads word lists and persists reading progress).
 * Both live in the same package; the index is re-read from disk on each
 * construction so a freshly cold-started service session sees current content.
 */
class DocumentStore(context: Context) {
    private val gson = Gson()
    private val dir = File(context.filesDir, "documents").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val lock = Any()

    private var index: MutableList<DocumentInfo> = loadIndex()

    @Volatile private var cachedDocId: String? = null
    @Volatile private var cachedWords: List<String> = emptyList()

    fun documents(): List<DocumentInfo> = synchronized(lock) { index.toList() }

    /** Re-reads the index from disk so a long-lived screen reflects documents
     *  added by another entry point (the share target, or the reader's progress). */
    fun reload() {
        synchronized(lock) { index = loadIndex() }
    }

    fun document(id: String): DocumentInfo? = synchronized(lock) { index.firstOrNull { it.id == id } }

    /** Adds a document from extracted plain text. Returns null when the text has no words. */
    fun add(title: String, text: String, source: String): DocumentInfo? {
        val words = tokenize(text)
        if (words.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        File(dir, "$id.txt").writeText(text)
        val info = DocumentInfo(
            id = id,
            title = title.ifBlank { words.take(6).joinToString(" ") { TextMarks.strip(it) }.take(48) },
            totalWords = words.size,
            progressWordIndex = 0,
            addedAtMs = System.currentTimeMillis(),
            source = source,
        )
        synchronized(lock) {
            index.add(0, info)
            persistIndex()
        }
        cachedDocId = id
        cachedWords = words
        return info
    }

    fun delete(id: String) {
        synchronized(lock) {
            index.removeAll { it.id == id }
            persistIndex()
        }
        File(dir, "$id.txt").delete()
        if (cachedDocId == id) {
            cachedDocId = null
            cachedWords = emptyList()
        }
    }

    fun updateProgress(id: String, wordIndex: Int) {
        synchronized(lock) {
            var changed = false
            index = index.map {
                if (it.id == id && it.progressWordIndex != wordIndex) {
                    changed = true
                    it.copy(progressWordIndex = wordIndex.coerceIn(0, it.totalWords))
                } else {
                    it
                }
            }.toMutableList()
            if (changed) persistIndex()
        }
    }

    /** The full tokenized word list of a document (cached for the last one used). */
    fun words(id: String): List<String> {
        if (cachedDocId == id) return cachedWords
        val file = File(dir, "$id.txt")
        if (!file.exists()) return emptyList()
        val words = tokenize(file.readText())
        cachedDocId = id
        cachedWords = words
        return words
    }

    /**
     * Splits text into display words, tagging the last word of each paragraph
     * with [TextMarks.PARAGRAPH] so the reader can pause longer between them.
     */
    private fun tokenize(text: String): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
        val out = ArrayList<String>()
        paragraphs.forEach { paragraph ->
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return@forEach
            out.addAll(words.subList(0, words.size - 1))
            out.add(words.last() + TextMarks.PARAGRAPH)
        }
        if (out.isNotEmpty()) {
            val last = out.size - 1
            out[last] = TextMarks.strip(out[last])
        }
        return out
    }

    private fun loadIndex(): MutableList<DocumentInfo> {
        if (!indexFile.exists()) return mutableListOf()
        return runCatching {
            val type = object : TypeToken<MutableList<DocumentInfo>>() {}.type
            gson.fromJson<MutableList<DocumentInfo>>(indexFile.readText(), type)
        }.getOrNull() ?: mutableListOf()
    }

    private fun persistIndex() {
        runCatching { indexFile.writeText(gson.toJson(index)) }
    }
}
