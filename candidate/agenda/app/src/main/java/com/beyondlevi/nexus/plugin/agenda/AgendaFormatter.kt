package com.beyondlevi.nexus.plugin.agenda

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * Turns [AgendaEvent]s into HUD rows. Pure JVM (no Android, no SDK types) so the
 * whole rendering contract is covered by unit tests.
 *
 * Three platform facts shape this class, all confirmed on RG-glasses:
 *  - a list row is drawn as `text` + `trail` (right of the title) + `sub`. The
 *    row `badge` is only rendered on prose rows, so the time of an event must
 *    ride in [AgendaRow.trail] — a badge is silently dropped;
 *  - a row title never wraps and is ellipsised around 28 columns, and the trail
 *    eats into that width (~0.8 column per trail character plus a gap), so the
 *    title is sized from the trail rather than clipped at a fixed length;
 *  - a surface JSON above ~3 KiB is only delivered while the SPP data plane is
 *    up, so rows are paged ([ROWS_PER_PAGE]) and per-row text is bounded.
 */
object AgendaFormatter {

    /** Rows emitted per payload page. The HUD windows further, to its viewport. */
    const val ROWS_PER_PAGE = 12

    /** Columns the renderer fits in a list-row title at full width. */
    const val ROW_TITLE_COLS = 28

    /** Columns the renderer fits in the smaller sub line. */
    const val ROW_SUB_COLS = 36

    private const val MAX_DETAIL_TEXT = 96

    /** Columns one wrapped line of a prose row fits on the RG-glasses (14.5sp). */
    const val PROSE_LINE_COLS = 29

    /** Wrapped lines a prose row draws before the renderer clips the rest. */
    const val PROSE_ROW_LINES = 3

    /**
     * Upper bound of a prose row, only used to bound previews. Real rows are cut
     * by [PROSE_ROW_LINES] wrapped lines, not by a character count: a row holding
     * a long URL fits far fewer characters, and the tail it loses is dropped
     * silently rather than reflowed.
     */
    const val PROSE_ROW_COLS = PROSE_LINE_COLS * PROSE_ROW_LINES

    /**
     * The mark that says "a tap opens this one".
     *
     * A prose row gets NO selection rail from the hub — `bodyRow` only swaps the
     * text colour, which is far too quiet to tell two stacked rows apart (it
     * misled the author of this plugin on device). The row says it itself.
     */
    const val FOCUS_MARK = "\u203a "
    private const val IMMINENT_MINUTES = 15L

    data class Page(
        val rows: List<AgendaRow>,
        val hiddenAbove: Int,
        val hiddenBelow: Int,
    )

    /** Collapse mirrored copies, then order for the HUD. */
    fun prepareForHud(events: List<AgendaEvent>, zone: ZoneId): List<AgendaEvent> =
        sortForHud(dedupe(events), zone)

    /**
     * One appointment, one row. The same meeting commonly lands in two calendars
     * (a work account plus an Outlook mirror), and on a HUD the wearer reads the
     * duplicate as two separate commitments. Same title, same start, same end is
     * the same appointment; the copy carrying the most detail wins.
     */
    fun dedupe(events: List<AgendaEvent>): List<AgendaEvent> = events
        .groupBy { Triple(it.title.trim().lowercase(), it.startMs, it.endMs) }
        .values
        .map { copies -> copies.maxByOrNull { detailWeight(it) } ?: copies.first() }
        .sortedWith(compareBy({ it.startMs }, { it.title }))

    private fun detailWeight(event: AgendaEvent): Int =
        (event.location?.length ?: 0) + (event.description?.length ?: 0)

    /**
     * Display order: what the wearer still has to do first, background entries
     * last. An all-day or multi-day entry ("Férias", "Ausente") is context, not a
     * commitment — sorted by start alone it hogs the top of the list for weeks
     * because it began before everything else.
     */
    fun sortForHud(events: List<AgendaEvent>, zone: ZoneId): List<AgendaEvent> =
        events.sortedWith(
            compareBy(
                { if (isBackground(it, zone)) 1 else 0 },
                { it.startMs },
                { it.title },
            ),
        )

    /** One row per event, in the same order as [events] — index == selection index. */
    fun listRows(
        events: List<AgendaEvent>,
        nowMs: Long,
        zone: ZoneId,
        locale: Locale,
        labels: AgendaLabels,
    ): List<AgendaRow> = events.map { event ->
        val trail = listOf(trailToken(event, nowMs, zone, locale, labels))
        AgendaRow(
            text = clip(event.title.ifBlank { labels.untitled }, titleColumns(trail)),
            trail = trail,
            sub = clip(subLine(event, nowMs, zone, locale, labels), ROW_SUB_COLS),
            tone = tone(event, nowMs, zone),
        )
    }

    /**
     * The one-line context under the card title. [capped] marks a list the source
     * truncated, so the count is a floor rather than a total.
     */
    fun listSubtitle(
        eventCount: Int,
        horizonDays: Int,
        capped: Boolean,
        labels: AgendaLabels,
    ): String = String.format(
        Locale.ROOT,
        labels.eventsCount,
        eventCount,
        horizonDays,
    ).let { if (capped) it.replaceFirst(eventCount.toString(), "$eventCount+") else it }

    /**
     * Slices [rows] into the viewport-sized page holding [selectedIndex]. Keeps the
     * payload small and the focused row inside the emitted slice.
     */
    fun page(rows: List<AgendaRow>, selectedIndex: Int, rowsPerPage: Int = ROWS_PER_PAGE): Page {
        if (rows.size <= rowsPerPage) return Page(rows, 0, 0)
        val safeIndex = selectedIndex.coerceIn(0, rows.lastIndex)
        val start = (safeIndex / rowsPerPage) * rowsPerPage
        val end = minOf(start + rowsPerPage, rows.size)
        return Page(rows.subList(start, end), start, rows.size - end)
    }

    /** Footer for the list view: position in the whole agenda plus the input hint. */
    fun listFooter(selectedIndex: Int, total: Int, labels: AgendaLabels): String =
        if (total <= 0) labels.listFooter else "${selectedIndex + 1}/$total - ${labels.listFooter}"

    /** Subtitle of the detail view: when the event runs, not how to navigate. */
    fun detailSubtitle(
        event: AgendaEvent,
        selectedIndex: Int,
        total: Int,
        zone: ZoneId,
        locale: Locale,
        labels: AgendaLabels,
    ): String {
        val position = if (total <= 0) "" else "${selectedIndex + 1}/$total - "
        return clip(position + whenLine(event, zone, locale, labels), ROW_SUB_COLS * 2)
    }

    /** Detail view of a single event. Not selectable — BACK returns to the list. */
    fun detailRows(
        event: AgendaEvent,
        attendees: List<AgendaAttendee>,
        nowMs: Long,
        zone: ZoneId,
        locale: Locale,
        labels: AgendaLabels,
    ): List<AgendaRow> = buildList {
        add(
            AgendaRow(
                text = fullDay(event.startMs, zone, locale),
                sub = whenLine(event, zone, locale, labels),
                tone = tone(event, nowMs, zone),
            ),
        )
        event.location?.takeIf { it.isNotBlank() }?.let {
            add(
                AgendaRow(
                    text = clip(it.replace(WHITESPACE, " "), MAX_DETAIL_TEXT),
                    badge = labels.detailWhere,
                    tone = AgendaTone.BODY,
                ),
            )
        }
        event.calendarName?.takeIf { it.isNotBlank() }?.let {
            add(AgendaRow(text = clip(it, ROW_SUB_COLS), tone = AgendaTone.DIM))
        }
        if (attendees.isNotEmpty()) {
            add(
                AgendaRow(
                    text = previewRow(participantsPreview(attendees, labels)),
                    badge = labels.detailWho,
                    tone = AgendaTone.BODY,
                    target = AgendaDetailTarget.PARTICIPANTS,
                ),
            )
        }
        plainText(event.description).takeIf { it.isNotBlank() }?.let {
            add(
                AgendaRow(
                    text = previewRow(it),
                    badge = labels.detailNotes,
                    tone = AgendaTone.BODY,
                    target = AgendaDetailTarget.NOTES,
                ),
            )
        }
    }

    /**
     * Marks the row a tap would open. Applied after the state has settled which
     * row is focused, so the formatter itself stays a pure function of content.
     */
    fun markFocused(rows: List<AgendaRow>, focused: AgendaDetailTarget?): List<AgendaRow> =
        rows.map { row ->
            if (row.target != null && row.target == focused) {
                row.copy(text = FOCUS_MARK + row.text)
            } else {
                row
            }
        }

    /** Footer of the detail: what a tap opens right now, named. */
    fun detailFooter(focused: AgendaDetailTarget?, labels: AgendaLabels): String = when (focused) {
        AgendaDetailTarget.PARTICIPANTS -> "${labels.openHint}: ${labels.participants}"
        AgendaDetailTarget.NOTES -> "${labels.openHint}: ${labels.notes}"
        null -> labels.detailFooter
    }

    /**
     * A preview cut to one prose row, leaving room for [FOCUS_MARK] and the
     * ellipsis — the row must not grow a fourth line when it takes the mark, or
     * the tail the wearer can see would change with the cursor.
     */
    private fun previewRow(text: String): String {
        val whole = text.trim()
        if (fitsPreview(whole)) return whole
        var words = whole.split(' ').filter { it.isNotEmpty() }
        while (words.isNotEmpty()) {
            val candidate = words.joinToString(" ").trimEnd(',', '-', ' ') + "\u2026"
            if (fitsPreview(candidate)) return candidate
            words = words.dropLast(1)
        }
        return whole.take(PROSE_LINE_COLS)
    }

    private fun fitsPreview(text: String): Boolean =
        renderedLineCount(FOCUS_MARK + text) <= PROSE_ROW_LINES

    /** "8 people - Ana, Bruno, Carla" — enough to recognise the meeting at a glance. */
    private fun participantsPreview(
        attendees: List<AgendaAttendee>,
        labels: AgendaLabels,
    ): String {
        val count = String.format(Locale.ROOT, labels.participantsCount, attendees.size)
        val names = attendees.joinToString(", ") { shortName(it) }
        return "$count - $names"
    }

    /** First name where there is one: a HUD row has no room for a full address. */
    private fun shortName(attendee: AgendaAttendee): String =
        displayName(attendee).substringBefore(" ")

    /**
     * What to call someone. Most invitations carry no display name at all — the
     * provider only has the address — and a truncated "rodrigo.miranda@beyondc…"
     * is unreadable on a HUD row, so the local part becomes the name and the full
     * address stays in the sub line.
     */
    fun displayName(attendee: AgendaAttendee): String {
        val name = attendee.name.trim()
        if (!name.contains("@")) return name
        return name.substringBefore("@")
            .split('.', '_', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.ROOT) } }
            .ifBlank { name }
    }

    /** The participants page: one row each, organiser first, RSVP on the right. */
    fun participantRows(
        attendees: List<AgendaAttendee>,
        labels: AgendaLabels,
    ): List<AgendaRow> = attendees.map { attendee ->
        val trail = listOf(rsvpLabel(attendee.rsvp, labels))
        AgendaRow(
            text = clip(displayName(attendee), titleColumns(trail)),
            trail = trail,
            sub = clip(
                listOfNotNull(
                    if (attendee.organizer) labels.organizer else null,
                    attendee.email,
                ).joinToString(" - "),
                ROW_SUB_COLS,
            ).ifBlank { null },
            tone = if (attendee.rsvp == AgendaRsvp.DECLINED) AgendaTone.DIM else AgendaTone.NORMAL,
        )
    }

    private fun rsvpLabel(rsvp: AgendaRsvp, labels: AgendaLabels): String = when (rsvp) {
        AgendaRsvp.ACCEPTED -> labels.rsvpAccepted
        AgendaRsvp.DECLINED -> labels.rsvpDeclined
        AgendaRsvp.TENTATIVE -> labels.rsvpTentative
        AgendaRsvp.PENDING -> labels.rsvpPending
    }

    /**
     * What a reader document may cost, in the two things the platform actually
     * measures: characters (the typed model) and **serialized bytes** (the SDK
     * preflight and the transport).
     */
    data class ReaderBudget(val chars: Int, val bytes: Int)

    /**
     * The budget for one reader payload.
     *
     * Two independent ceilings, and counting characters catches neither:
     *  - `sendSurface` rejects a payload whose serialized JSON exceeds **64 KiB**
     *    (`INVALID_PAYLOAD`), and the SPP data plane does not relax it. 40,000
     *    characters is inside the model's char cap yet up to ~80 KB of accented
     *    pt-BR or CJK text, so the surface would silently never appear and the
     *    wearer would stay on the previous screen.
     *  - on a control-channel-only link the framed envelope must stay under
     *    ~3 KiB (see §1 of the field gotchas), which is bytes again.
     *
     * [shellBytes] is what the caller will spend on title, subtitle, footer and
     * contentKey — measured, not assumed: a long accented title is worth three
     * times its character count.
     */
    fun readerBudget(shellBytes: Int, dataPlaneUp: Boolean): ReaderBudget {
        val cap = if (dataPlaneUp) MAX_PAYLOAD_BYTES else CXR_SAFE_BYTES
        val bytes = cap - PAYLOAD_FRAMING_BYTES - shellBytes
        return ReaderBudget(chars = MAX_TOTAL_CHARS, bytes = bytes.coerceAtLeast(0))
    }

    /**
     * The whole description as reader segments, one PROSE segment per paragraph.
     *
     * A reader surface has no three-line clamp and no pages: the glasses renderer
     * wraps and scrolls the document itself, so nothing here computes geometry.
     * What it does enforce is every limit that would otherwise reject the payload
     * — the model's character caps, the 240-segment cap, and the serialized byte
     * budget from [readerBudget]. A slot, its characters AND its bytes are held
     * back for the closing notice, so a document that had to be cut always says
     * so instead of just stopping.
     */
    fun readerSegments(
        description: String?,
        labels: AgendaLabels,
        budget: ReaderBudget = ReaderBudget(MAX_TOTAL_CHARS, MAX_PAYLOAD_BYTES - PAYLOAD_FRAMING_BYTES),
    ): List<AgendaSegment> {
        val paragraphs = plainText(description, keepBreaks = true)
            .split(PARAGRAPH_BREAK)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { paragraph -> paragraph.chunked(MAX_SEGMENT_CHARS) }
        if (paragraphs.isEmpty()) return emptyList()

        val notice = labels.readerTruncated
        var charsLeft = budget.chars.coerceAtMost(MAX_TOTAL_CHARS) - notice.length
        var bytesLeft = budget.bytes - segmentByteCost(notice)
        val segments = mutableListOf<AgendaSegment>()
        var truncated = false

        for (paragraph in paragraphs) {
            // MAX_SEGMENTS - 1: the closing notice needs a slot of its own, or a
            // cut document loses its marker (the cap rejects the extra segment).
            if (segments.size >= MAX_SEGMENTS - 1) {
                truncated = true
                break
            }
            val cost = segmentByteCost(paragraph)
            if (paragraph.length <= charsLeft && cost <= bytesLeft) {
                segments += AgendaSegment(AgendaSegmentKind.PROSE, paragraph)
                charsLeft -= paragraph.length
                bytesLeft -= cost
                continue
            }
            // It does not fit whole. Send as much of its beginning as the budget
            // carries rather than nothing: a wearer who asked for the notes wants
            // the first lines far more than an apology on its own.
            val prefix = longestPrefix(paragraph, charsLeft, bytesLeft)
            if (prefix.isNotEmpty()) {
                segments += AgendaSegment(AgendaSegmentKind.PROSE, prefix)
            }
            truncated = true
            break
        }
        if (truncated) segments += AgendaSegment(AgendaSegmentKind.ASIDE, notice)
        return segments
    }

    /**
     * The longest beginning of [text] that fits both budgets, backed off to a word
     * boundary when one is close enough to be worth it.
     */
    private fun longestPrefix(text: String, charsLeft: Int, bytesLeft: Int): String {
        var used = SEGMENT_ENVELOPE_BYTES
        var end = 0
        while (end < text.length && end < charsLeft) {
            val next = used + jsonByteCost(text[end].toString())
            if (next > bytesLeft) break
            used = next
            end++
        }
        if (end <= 0) return ""
        val cut = text.lastIndexOf(' ', end - 1)
        val boundary = if (cut >= end - WORD_BOUNDARY_SLACK && cut > 0) cut else end
        return text.take(boundary).trimEnd()
    }

    /** Bytes one segment costs on the wire: escaped UTF-8 text plus its envelope. */
    fun segmentByteCost(text: String): Int = jsonByteCost(text) + SEGMENT_ENVELOPE_BYTES

    /**
     * Serialized size of [text] inside JSON. Escaping matters: a quote-heavy or
     * newline-heavy description grows past its UTF-8 length, and that growth is
     * measured by the same preflight that rejects the payload.
     */
    fun jsonByteCost(text: String): Int {
        var bytes = 0
        for (ch in text) {
            bytes += when {
                ch == '"' || ch == '\\' -> 2
                ch < ' ' -> 6
                ch.code < 0x80 -> 1
                ch.code < 0x800 -> 2
                ch.isSurrogate() -> 2
                else -> 3
            }
        }
        return bytes
    }

    /**
     * Calendar descriptions are HTML, not prose: Google and Outlook both ship
     * `<br />`, anchor tags and entities, plus a decorative rule of `-::~:~::~`
     * that eats a whole HUD page. None of that survives to the glasses.
     *
     * [keepBreaks] preserves paragraph structure, which a reader wants and a
     * one-row preview does not.
     */
    fun plainText(raw: String?, keepBreaks: Boolean = false): String {
        if (raw.isNullOrBlank()) return ""
        var text: String = raw
        val blockBreak = if (keepBreaks) "\n" else " "
        BLOCK_TAGS.forEach { text = it.replace(text, blockBreak) }
        text = TAG.replace(text, "")
        ENTITIES.forEach { (entity, char) -> text = text.replace(entity, char) }
        text = NUMERIC_ENTITY.replace(text) { match ->
            match.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) } ?: ""
        }
        text = text.lines().joinToString("\n") { line ->
            line.split(' ', '\t')
                .filterNot { token -> token.length >= DIVIDER_MIN && token.all { it in DIVIDER_CHARS } }
                .joinToString(" ")
        }
        return if (keepBreaks) {
            text.lines().joinToString("\n") { it.replace(HORIZONTAL_SPACE, " ").trim() }.trim()
        } else {
            text.replace(WHITESPACE, " ").trim()
        }
    }

    /**
     * How many lines the HUD wraps [text] into.
     *
     * Modelling the break opportunities matters: the renderer does not cut a long
     * token at the column edge, it breaks it at `/`, `-`, `_`, `&`, `?` and `=`
     * like any line breaker, which wastes the tail of the previous line. Counting
     * as if the cut were exact under-counts the lines and lets a row through that
     * the HUD then clips — verified on device with a Roam meeting URL.
     */
    fun renderedLineCount(text: String, columns: Int = PROSE_LINE_COLS): Int {
        var lines = 1
        var used = 0

        fun place(piece: String) {
            var rest = piece
            while (rest.isNotEmpty()) {
                val room = columns - used
                if (rest.length <= room) {
                    used += rest.length
                    return
                }
                if (used > 0) {
                    lines++
                    used = 0
                    continue
                }
                // Longer than a whole line even from column zero: hard split.
                rest = rest.drop(columns)
                lines++
                used = 0
            }
        }

        text.split(' ').filter { it.isNotEmpty() }.forEachIndexed { index, word ->
            if (index > 0) {
                if (used + 1 <= columns) used += 1 else { lines++; used = 0 }
            }
            if (word.length <= columns - used) {
                used += word.length
            } else {
                breakSegments(word).forEach { place(it) }
            }
        }
        return lines
    }

    /** A token split at the points a line breaker is allowed to use. */
    private fun breakSegments(word: String): List<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        word.forEach { char ->
            if (char in BREAK_BEFORE && current.isNotEmpty()) {
                segments += current.toString()
                current.clear()
            }
            current.append(char)
        }
        if (current.isNotEmpty()) segments += current.toString()
        return segments
    }

    /** Columns left for the title once the trail has taken its share of the row. */
    fun titleColumns(trail: List<String>): Int {
        if (trail.isEmpty()) return ROW_TITLE_COLS
        val trailChars = trail.sumOf { it.length } + (trail.size - 1)
        val cost = ceil(trailChars * TRAIL_COLUMN_COST).toInt() + 1
        return (ROW_TITLE_COLS - cost).coerceAtLeast(MIN_TITLE_COLS)
    }

    /** The right-hand token: what the wearer needs to know about timing at a glance. */
    private fun trailToken(
        event: AgendaEvent,
        nowMs: Long,
        zone: ZoneId,
        locale: Locale,
        labels: AgendaLabels,
    ): String = when {
        event.allDay -> labels.allDay
        isOngoing(event, nowMs) -> labels.now
        else -> time(event.startMs, zone, locale)
    }

    private fun subLine(
        event: AgendaEvent,
        nowMs: Long,
        zone: ZoneId,
        locale: Locale,
        labels: AgendaLabels,
    ): String {
        val parts = mutableListOf<String>()
        val startedBeforeToday = date(event.startMs, zone) < date(nowMs, zone)
        if (startedBeforeToday) {
            // The start day is history; what still matters is when it releases us.
            parts += "${labels.untilPrefix} ${endStamp(event, zone, locale, labels)}"
        } else {
            parts += dayLabel(event.startMs, nowMs, zone, locale, labels)
            if (!event.allDay) {
                parts += "${labels.untilPrefix} ${endStamp(event, zone, locale, labels)}"
            }
        }
        event.location?.takeIf { it.isNotBlank() }?.let { parts += it }
        return parts.joinToString(" - ")
    }

    /**
     * When the event ends, spelled so it is never the bare "00:00" of a midnight
     * boundary: an entry that ends at midnight ends *on the day before*, and one
     * that ends on another day needs that day named, not just a clock time.
     */
    private fun endStamp(
        event: AgendaEvent,
        zone: ZoneId,
        locale: Locale,
        labels: AgendaLabels,
    ): String {
        val endsAtMidnight = isMidnight(event.endMs, zone)
        val lastMoment = if (event.endMs > event.startMs) event.endMs - 1 else event.endMs
        val endDay = date(lastMoment, zone)
        val sameDay = endDay == date(event.startMs, zone)
        return when {
            sameDay && !event.allDay -> time(event.endMs, zone, locale)
            sameDay -> labels.allDay
            endsAtMidnight || event.allDay -> shortDay(lastMoment, zone, locale)
            else -> "${shortDay(event.endMs, zone, locale)} ${time(event.endMs, zone, locale)}"
        }
    }

    /** The human sentence for when an event runs — used by the detail view. */
    private fun whenLine(
        event: AgendaEvent,
        zone: ZoneId,
        locale: Locale,
        labels: AgendaLabels,
    ): String = if (event.allDay) {
        val end = endStamp(event, zone, locale, labels)
        if (end == labels.allDay) labels.allDay else "${labels.untilPrefix} $end"
    } else {
        "${time(event.startMs, zone, locale)} - ${endStamp(event, zone, locale, labels)}"
    }

    private fun tone(event: AgendaEvent, nowMs: Long, zone: ZoneId): AgendaTone {
        // Background entries never claim the brightest treatment: they would hold
        // the wearer's attention for days without ever being urgent.
        if (isBackground(event, zone)) {
            return if (date(event.startMs, zone) <= date(nowMs, zone)) {
                AgendaTone.NORMAL
            } else {
                AgendaTone.DIM
            }
        }
        if (isOngoing(event, nowMs)) return AgendaTone.ALERT
        val minutesAway = Duration.ofMillis(event.startMs - nowMs).toMinutes()
        if (minutesAway in 0..IMMINENT_MINUTES) return AgendaTone.ALERT
        val today = date(nowMs, zone)
        return if (date(event.startMs, zone) <= today.plusDays(1)) {
            AgendaTone.NORMAL
        } else {
            AgendaTone.DIM
        }
    }

    /** All-day, or spanning more than one calendar day: context rather than a slot. */
    private fun isBackground(event: AgendaEvent, zone: ZoneId): Boolean {
        if (event.allDay) return true
        val lastMoment = if (event.endMs > event.startMs) event.endMs - 1 else event.endMs
        return date(lastMoment, zone) != date(event.startMs, zone)
    }

    private fun isOngoing(event: AgendaEvent, nowMs: Long): Boolean =
        event.startMs <= nowMs && nowMs < event.endMs

    private fun isMidnight(epochMs: Long, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().toSecondOfDay() == 0

    private fun dayLabel(
        startMs: Long,
        nowMs: Long,
        zone: ZoneId,
        locale: Locale,
        labels: AgendaLabels,
    ): String {
        val today = date(nowMs, zone)
        return when (date(startMs, zone)) {
            today -> labels.today
            today.plusDays(1) -> labels.tomorrow
            else -> shortDay(startMs, zone, locale)
        }
    }

    private fun shortDay(epochMs: Long, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale)
            .format(Instant.ofEpochMilli(epochMs).atZone(zone))

    private fun fullDay(startMs: Long, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
            .format(Instant.ofEpochMilli(startMs).atZone(zone))

    private fun time(epochMs: Long, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(epochMs).atZone(zone))

    private fun date(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun clip(value: String, max: Int): String {
        val single = value.trim().replace(WHITESPACE, " ")
        return if (single.length <= max) single else single.take(max - 1).trimEnd() + "…"
    }

    /** A trail character costs about this much of the title's width, plus a gap. */
    private const val TRAIL_COLUMN_COST = 0.8
    private const val MIN_TITLE_COLS = 12
    private const val DIVIDER_MIN = 6
    private const val MAX_SEGMENTS = 240
    private const val MAX_SEGMENT_CHARS = 4_096
    private const val MAX_TOTAL_CHARS = 40_000

    /** The SDK rejects a surface whose serialized JSON passes this (INVALID_PAYLOAD). */
    const val MAX_PAYLOAD_BYTES = 64 * 1024

    /** What a control-channel-only link carries once framing is counted (§1). */
    const val CXR_SAFE_BYTES = 2_600

    /** Envelope of the payload itself: surfaceId, kind, flags, seq, ids. */
    private const val PAYLOAD_FRAMING_BYTES = 512

    /** `{"kind":"prose","text":""},` and friends, per segment. */
    private const val SEGMENT_ENVELOPE_BYTES = 32

    /** How far back a word boundary may pull a cut before it is not worth it. */
    private const val WORD_BOUNDARY_SLACK = 24
    private val PARAGRAPH_BREAK = Regex("\n+")
    private val HORIZONTAL_SPACE = Regex("[ \t\r]+")
    private const val BREAK_BEFORE = "/-_&?="
    private const val DIVIDER_CHARS = "-:~_=*·—"
    private val TAG = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(\\d+);")
    private val BLOCK_TAGS = listOf(
        Regex("(?i)<br\\s*/?>"),
        Regex("(?i)</(p|div|li|tr|h[1-6])>"),
    )
    private val ENTITIES = listOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
    )
    private val WHITESPACE = Regex("\\s+")
}
