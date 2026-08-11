package com.beyondlevi.nexus.lume

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusPlaybackAnchor
import com.anezium.rokidbus.client.plugin.NexusTimedLine
import com.anezium.rokidbus.client.plugin.NexusTimedLines
import com.anezium.rokidbus.shared.plugin.NexusInputEvent
import java.security.MessageDigest

/**
 * The Lume HUD runtime: a two-view (LIBRARY -> READER) one-axis state machine.
 *
 * ## Reader rendering (two distinct modes)
 *
 * - **Playing**: a *single-entry* `NexusTimedLines` surface. With only one line
 *   the hub's lyrics renderer has no previous/next lines to show, so the HUD
 *   displays ONLY the active word, large and centred — no flanking words to
 *   distract. A phone-side ticker advances word by word at the RsvpEngine pace
 *   (payload is tiny, so it rides CXR with no SPP needed).
 * - **Paused**: a `NexusCard` — the active word as the title, and below it the
 *   surrounding sentences (>= 2) as the body for re-anchoring context. NEXT/PREV
 *   step between sentences (the body follows), exactly like before.
 *
 * Ring mapping (R08): NEXT/PREV = library move, or (reader) speed while playing /
 * sentence step while paused. SELECT = open / play-pause. BACK = exit.
 * Documents open paused; a tap starts playback.
 */
class LumeRuntime(private val host: Host, private val store: DocumentStore, private val settings: SettingsStore) {

    interface Host {
        fun showCard(card: NexusCard, show: Boolean)
        fun showTimedLines(lines: NexusTimedLines, show: Boolean)
        fun hide()
    }

    private enum class View { LIBRARY, READER }

    private var active = false
    private var shown = false
    private var view = View.LIBRARY
    private val library = LibrarySelection()
    private var documents: List<DocumentInfo> = emptyList()

    // Reader session state.
    private var model: ReaderModel? = null
    private var docId: String = ""
    private var docTitle: String = ""
    private var wpm: Int = RsvpEngine.DEFAULT_WPM
    private var currentIndex: Int = 0
    private var playing: Boolean = false
    private var lastPersistElapsed: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val playTick = object : Runnable {
        override fun run() {
            if (!active || view != View.READER || !playing) return
            val m = model ?: return
            if (currentIndex >= m.size - 1) {
                // End of document: settle on the last word, paused.
                playing = false
                renderPause()
                persistProgress()
                return
            }
            currentIndex += 1
            renderPlayWord()
            maybePersist()
            handler.postDelayed(this, RsvpEngine.delayMsFor(m.rawWord(currentIndex), wpm).coerceAtLeast(MIN_TICK_MS))
        }
    }

    fun open() {
        active = true
        shown = false
        view = View.LIBRARY
        refreshLibrary()
        renderLibrary()
    }

    fun close() {
        if (!active) return
        persistProgress()
        stopTicker()
        active = false
        model = null
        host.hide()
    }

    fun registrationApproved() {
        if (!active) return
        when (view) {
            View.LIBRARY -> renderLibrary()
            View.READER -> if (playing) renderPlayWord() else renderPause()
        }
    }

    fun input(event: NexusInputEvent) {
        if (!active) return
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            -> onNext()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            -> onPrev()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            -> onSelect()
            KeyEvent.KEYCODE_BACK -> close()
            else -> Unit
        }
    }

    /* ---------------- library view ---------------- */

    private fun refreshLibrary() {
        documents = store.documents()
        library.setCount(documents.size)
    }

    private fun onNext() {
        when (view) {
            View.LIBRARY -> { library.move(1); renderLibrary() }
            View.READER -> if (playing) changeSpeed(RsvpEngine.WPM_STEP) else stepSentence(1)
        }
    }

    private fun onPrev() {
        when (view) {
            View.LIBRARY -> { library.move(-1); renderLibrary() }
            View.READER -> if (playing) changeSpeed(-RsvpEngine.WPM_STEP) else stepSentence(-1)
        }
    }

    private fun onSelect() {
        when (view) {
            View.LIBRARY -> library.selected()?.let { openDocument(documents[it]) }
            View.READER -> togglePlayback()
        }
    }

    private fun renderLibrary() {
        val lang = settings.language
        val card = if (documents.isEmpty()) {
            NexusCard(
                title = "Lume",
                lines = listOf(Strings.emptyLibrary(lang)),
                footer = Strings.libraryEmptyFooter(lang),
                contentKey = shortHash("empty"),
                handlesBack = true,
            )
        } else {
            val selected = library.selectedIndex
            val rows = rowWindow(documents.size, selected)
            val lines = rows.map { i ->
                val doc = documents[i]
                val marker = if (i == selected) ">" else " "
                val pct = if (doc.totalWords > 0) doc.progressWordIndex * 100 / doc.totalWords else 0
                "$marker ${doc.title}  (${doc.source} · $pct%)".take(MAX_LINE_CHARS)
            }
            NexusCard(
                title = Strings.libraryTitle(lang),
                lines = lines,
                footer = Strings.libraryFooter(lang),
                contentKey = shortHash("lib:${documents.size}:$selected:${documents.getOrNull(selected)?.id}"),
                handlesBack = true,
            )
        }
        host.showCard(card, consumeShow())
    }

    private fun renderMessage(message: String) {
        host.showCard(
            NexusCard(
                title = "Lume",
                lines = listOf(message),
                footer = Strings.libraryEmptyFooter(settings.language),
                contentKey = shortHash("msg:$message"),
                handlesBack = true,
            ),
            consumeShow(),
        )
    }

    /* ---------------- reader view ---------------- */

    private fun openDocument(doc: DocumentInfo) {
        val words = store.words(doc.id)
        if (words.isEmpty()) {
            renderMessage(Strings.cannotOpen(settings.language))
            return
        }
        docId = doc.id
        docTitle = doc.title
        wpm = settings.lastWpm
        val m = ReaderModel(words, wpm)
        model = m
        currentIndex = doc.progressWordIndex.coerceIn(0, (m.size - 1).coerceAtLeast(0))
        playing = false
        view = View.READER
        renderPause() // open paused: show the word + surrounding context
    }

    private fun togglePlayback() {
        val m = model ?: return
        if (playing) {
            playing = false
            stopTicker()
            renderPause()
            persistProgress()
        } else {
            if (currentIndex >= m.size - 1) currentIndex = 0 // replay from start if finished
            playing = true
            renderPlayWord()
            startTicker()
        }
    }

    private fun changeSpeed(delta: Int) {
        val m = model ?: return
        m.setWpm(m.wpm + delta)
        wpm = m.wpm
        settings.lastWpm = wpm
        // Playback pace picks up the new wpm on the next scheduled word; the
        // playing surface stays a single word, so nothing else to re-render.
    }

    private fun stepSentence(dir: Int) {
        val m = model ?: return
        currentIndex = m.sentenceStep(currentIndex, dir)
        renderPause()
        persistProgress()
    }

    private fun renderPlayWord() {
        val m = model ?: return
        val word = m.displayWord(currentIndex).take(MAX_LINE_CHARS).ifBlank { "·" }
        val surface = NexusTimedLines(
            // Zero-width title: NexusTimedLines requires a non-blank title, but a
            // visible book title marquee-scrolls and resets, stealing focus. A
            // zero-width space satisfies the check yet renders nothing, so during
            // playback the HUD shows ONLY the active word.
            title = ZERO_WIDTH_TITLE,
            contentKey = shortHash("play:$docId:$currentIndex:$wpm"),
            lines = listOf(NexusTimedLine(0L, word)),
            anchor = NexusPlaybackAnchor(positionMs = 0L, playing = false, sentAtElapsedRealtime = SystemClock.elapsedRealtime()),
            subtitle = null,
            footer = null,
        )
        host.showTimedLines(surface, consumeShow())
    }

    private fun renderPause() {
        val m = model ?: return
        val lang = settings.language
        val word = m.displayWord(currentIndex).take(READER_TITLE_CHARS).ifBlank { "Lume" }
        val context = chunkContext(m.contextWords(currentIndex, MIN_SENTENCES, MAX_CONTEXT_WORDS))
        val pct = if (m.size > 0) currentIndex * 100 / m.size else 0
        val card = NexusCard(
            title = word,
            lines = context,
            footer = "$wpm wpm · $pct% · ${Strings.readerPausedFooter(lang)}".take(MAX_LINE_CHARS),
            contentKey = shortHash("pause:$docId:$currentIndex"),
            handlesBack = true,
        )
        host.showCard(card, consumeShow())
    }

    /** Wraps context words into card rows (each <= MAX_LINE_CHARS), capped at MAX_CONTEXT_ROWS. */
    private fun chunkContext(words: List<String>): List<String> {
        if (words.isEmpty()) return emptyList()
        val rows = ArrayList<String>()
        val sb = StringBuilder()
        for (w in words) {
            if (sb.isNotEmpty() && sb.length + 1 + w.length > CONTEXT_ROW_CHARS) {
                rows.add(sb.toString())
                sb.setLength(0)
                if (rows.size >= MAX_CONTEXT_ROWS) return rows
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w)
        }
        if (sb.isNotEmpty() && rows.size < MAX_CONTEXT_ROWS) rows.add(sb.toString())
        return rows.map { it.take(MAX_LINE_CHARS) }
    }

    private fun maybePersist() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPersistElapsed >= PERSIST_INTERVAL_MS) persistProgress()
    }

    private fun persistProgress() {
        if (docId.isNotEmpty()) {
            store.updateProgress(docId, currentIndex)
            lastPersistElapsed = SystemClock.elapsedRealtime()
        }
    }

    private fun consumeShow(): Boolean {
        val show = !shown
        shown = true
        return show
    }

    private fun startTicker() {
        val m = model ?: return
        handler.removeCallbacks(playTick)
        handler.postDelayed(playTick, RsvpEngine.delayMsFor(m.rawWord(currentIndex), wpm).coerceAtLeast(MIN_TICK_MS))
    }

    private fun stopTicker() {
        handler.removeCallbacks(playTick)
    }

    private fun rowWindow(count: Int, selected: Int): IntRange {
        if (count <= MAX_LINES) return 0 until count
        val start = (selected - MAX_LINES / 2).coerceIn(0, count - MAX_LINES)
        return start until (start + MAX_LINES)
    }

    private fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(16) {
            for (index in 0 until 8) {
                val b = digest[index].toInt() and 0xff
                append(hex[b ushr 4]); append(hex[b and 0x0f])
            }
        }
    }

    private companion object {
        const val MIN_TICK_MS = 40L
        const val ZERO_WIDTH_TITLE = "​"
        const val PERSIST_INTERVAL_MS = 4_000L
        const val MIN_SENTENCES = 2
        const val MAX_CONTEXT_WORDS = 70
        const val MAX_CONTEXT_ROWS = 12
        const val CONTEXT_ROW_CHARS = 180
        const val READER_TITLE_CHARS = 80
        const val MAX_LINE_CHARS = 240
        const val MAX_LINES = 64
    }
}
