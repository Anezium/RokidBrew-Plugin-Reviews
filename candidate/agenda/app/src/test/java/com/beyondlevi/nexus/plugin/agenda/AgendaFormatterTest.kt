package com.beyondlevi.nexus.plugin.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

/**
 * Rendering is asserted against an injected clock and zone — an age or a "today"
 * computed from the wall clock inside the row builder makes a surface untestable.
 */
class AgendaFormatterTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val locale: Locale = Locale.forLanguageTag("pt-BR")
    private val labels = AgendaLabels()

    /** 2026-08-06 14:00 local. */
    private val now = at(2026, 8, 6, 14, 0)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun event(
        id: String = "1",
        title: String = "Reunião",
        start: Long = at(2026, 8, 6, 15, 0),
        end: Long = at(2026, 8, 6, 16, 0),
        allDay: Boolean = false,
        location: String? = null,
        description: String? = null,
        calendarName: String? = null,
    ) = AgendaEvent(
        id = id,
        eventId = id.hashCode().toLong(),
        title = title,
        startMs = start,
        endMs = end,
        allDay = allDay,
        location = location,
        description = description,
        calendarName = calendarName,
    )

    private fun rows(vararg events: AgendaEvent) =
        AgendaFormatter.listRows(events.toList(), now, zone, locale, labels)

    private fun attendee(
        name: String,
        email: String? = "$name@example.com".lowercase(),
        rsvp: AgendaRsvp = AgendaRsvp.ACCEPTED,
        organizer: Boolean = false,
    ) = AgendaAttendee(name, email, rsvp, organizer, self = false)

    private fun detail(
        event: AgendaEvent = event(),
        attendees: List<AgendaAttendee> = emptyList(),
    ) = AgendaFormatter.detailRows(event, attendees, now, zone, locale, labels)

    @Test
    fun `a row carries the title, the start time and the day`() {
        val row = rows(event()).single()
        assertEquals("Reunião", row.text)
        // The time rides in the trail: the hub does not draw a badge on a list row.
        assertTrue(row.trail.single().contains("15"))
        assertNull(row.badge)
        assertTrue(row.sub!!.startsWith(labels.today))
        assertTrue(row.sub!!.contains(labels.untilPrefix))
    }

    @Test
    fun `an event happening right now is badged and raised to ALERT`() {
        val row = rows(
            event(start = at(2026, 8, 6, 13, 30), end = at(2026, 8, 6, 14, 30)),
        ).single()
        assertEquals(labels.now, row.trail.single())
        assertEquals(AgendaTone.ALERT, row.tone)
    }

    @Test
    fun `an event starting within a quarter of an hour is already ALERT`() {
        val row = rows(
            event(start = at(2026, 8, 6, 14, 10), end = at(2026, 8, 6, 15, 0)),
        ).single()
        assertEquals(AgendaTone.ALERT, row.tone)
    }

    @Test
    fun `a later day is dimmed and named`() {
        val row = rows(
            event(start = at(2026, 8, 10, 9, 0), end = at(2026, 8, 10, 10, 0)),
        ).single()
        assertEquals(AgendaTone.DIM, row.tone)
        assertTrue(row.sub!!.isNotBlank())
    }

    @Test
    fun `tomorrow is named rather than dated`() {
        val row = rows(
            event(start = at(2026, 8, 7, 9, 0), end = at(2026, 8, 7, 10, 0)),
        ).single()
        assertTrue(row.sub!!.startsWith(labels.tomorrow))
    }

    @Test
    fun `an all-day event says so instead of showing a start time`() {
        val row = rows(
            event(
                start = at(2026, 8, 6, 0, 0),
                end = at(2026, 8, 7, 0, 0),
                allDay = true,
            ),
        ).single()
        assertEquals(labels.allDay, row.trail.single())
        assertTrue(!row.sub!!.contains(labels.untilPrefix))
        // Ongoing by the clock all day long, but never the brightest row.
        assertEquals(AgendaTone.NORMAL, row.tone)
    }

    @Test
    fun `a long title is clipped, never wrapped — a list row title is one line`() {
        val row = rows(event(title = "T".repeat(300))).single()
        // The trail takes its share of the row before the title gets any.
        assertEquals(AgendaFormatter.titleColumns(row.trail), row.text.length)
        assertTrue(row.text.length < AgendaFormatter.ROW_TITLE_COLS)
        assertTrue(row.text.endsWith("…"))
    }

    @Test
    fun `a wider trail leaves a narrower title`() {
        assertEquals(AgendaFormatter.ROW_TITLE_COLS, AgendaFormatter.titleColumns(emptyList()))
        assertTrue(
            AgendaFormatter.titleColumns(listOf("dia todo")) <
                AgendaFormatter.titleColumns(listOf("9:00")),
        )
    }

    @Test
    fun `a long location cannot blow the sub line budget`() {
        val row = rows(event(location = "L".repeat(300))).single()
        assertTrue(row.sub!!.length <= AgendaFormatter.ROW_SUB_COLS)
    }

    @Test
    fun `a blank title still renders a row`() {
        val row = rows(event(title = "   ")).single()
        assertTrue(row.text.isNotBlank())
    }

    @Test
    fun `paging keeps the focused row inside the emitted slice`() {
        val many = (0 until 40).map { event(id = "$it", title = "E$it") }
        val allRows = AgendaFormatter.listRows(many, now, zone, locale, labels)
        listOf(0, 11, 12, 25, 39).forEach { focus ->
            val page = AgendaFormatter.page(allRows, focus)
            val indexOnPage = focus - page.hiddenAbove
            assertTrue("focus $focus fell outside the page", indexOnPage in page.rows.indices)
            assertTrue(page.rows.size <= AgendaFormatter.ROWS_PER_PAGE)
            assertEquals(allRows[focus], page.rows[indexOnPage])
        }
    }

    @Test
    fun `a short agenda is emitted whole`() {
        val few = (0 until 5).map { event(id = "$it") }
        val page = AgendaFormatter.page(AgendaFormatter.listRows(few, now, zone, locale, labels), 4)
        assertEquals(5, page.rows.size)
        assertEquals(0, page.hiddenAbove)
        assertEquals(0, page.hiddenBelow)
    }

    @Test
    fun `an event that started on an earlier day leads with when it ends`() {
        val row = rows(
            event(
                start = at(2026, 7, 27, 9, 0),
                end = at(2026, 8, 12, 0, 0),
            ),
        ).single()
        // Never the start day (history) and never a bare midnight "00:00".
        assertTrue(row.sub!!.startsWith(labels.untilPrefix))
        assertTrue(!row.sub!!.contains("00:00"))
        assertTrue(row.sub!!.contains("11"))
    }

    @Test
    fun `an evening that runs to midnight keeps the clock, it never grows a day`() {
        // 22:00 -> 00:00 is one evening, and "until 00:00" reads right. Only an
        // event that really covers another day needs that day named.
        val row = rows(
            event(start = at(2026, 8, 6, 22, 0), end = at(2026, 8, 7, 0, 0)),
        ).single()
        assertEquals("${labels.today} - ${labels.untilPrefix} 00:00", row.sub)
    }

    @Test
    fun `a multi-day event ending at midnight names the last day it covers`() {
        val row = rows(
            event(start = at(2026, 8, 6, 9, 0), end = at(2026, 8, 9, 0, 0)),
        ).single()
        // Ends at midnight opening the 9th, so the last day it covers is the 8th.
        assertTrue("sub was: ${row.sub}", row.sub!!.contains("8"))
        assertTrue("sub was: ${row.sub}", !row.sub!!.contains("00:00"))
    }

    @Test
    fun `background entries sort after the day's real commitments`() {
        val vacation = event(
            id = "bg",
            title = "Férias",
            start = at(2026, 7, 27, 0, 0),
            end = at(2026, 8, 20, 0, 0),
            allDay = true,
        )
        val meeting = event(id = "m", title = "Daily", start = at(2026, 8, 6, 15, 0))
        val later = event(
            id = "l",
            title = "Retro",
            start = at(2026, 8, 6, 17, 0),
            end = at(2026, 8, 6, 18, 0),
        )
        val sorted = AgendaFormatter.sortForHud(listOf(vacation, later, meeting), zone)
        assertEquals(listOf("m", "l", "bg"), sorted.map { it.id })
    }

    @Test
    fun `the same meeting mirrored into two calendars is one row`() {
        val google = event(id = "g", title = "Reuniao semanal", calendarName = "Google")
        val outlookMirror = event(
            id = "o",
            title = "Reuniao semanal",
            calendarName = "Outlook",
            location = "Sala virtual",
            description = "Copia espelhada automaticamente do Outlook",
        )
        val prepared = AgendaFormatter.prepareForHud(listOf(google, outlookMirror), zone)
        assertEquals(1, prepared.size)
        // The copy that actually carries the detail is the one worth keeping.
        assertEquals("o", prepared.single().id)
    }

    @Test
    fun `two different meetings at the same time both survive`() {
        val a = event(id = "a", title = "Reuniao A")
        val b = event(id = "b", title = "Reuniao B")
        assertEquals(2, AgendaFormatter.prepareForHud(listOf(a, b), zone).size)
    }

    @Test
    fun `the same title at a different hour is not a duplicate`() {
        val morning = event(id = "m", start = at(2026, 8, 6, 9, 0), end = at(2026, 8, 6, 9, 30))
        val evening = event(id = "e", start = at(2026, 8, 6, 18, 0), end = at(2026, 8, 6, 18, 30))
        assertEquals(2, AgendaFormatter.prepareForHud(listOf(morning, evening), zone).size)
    }

    @Test
    fun `a truncated agenda says the count is a floor`() {
        assertTrue(
            AgendaFormatter.listSubtitle(120, 7, capped = true, labels = labels).contains("120+"),
        )
        assertTrue(
            !AgendaFormatter.listSubtitle(9, 7, capped = false, labels = labels).contains("+"),
        )
    }

    @Test
    fun `the detail subtitle states when the event runs, not how to navigate`() {
        val subtitle = AgendaFormatter.detailSubtitle(event(), 1, 40, zone, locale, labels)
        assertTrue(subtitle.startsWith("2/40"))
        assertTrue(subtitle.contains("15"))
        assertTrue(!subtitle.contains(labels.listFooter))
    }

    @Test
    fun `the detail view carries the day, the range and the prose`() {
        val detail = detail(
            event(
                location = "Sala 2",
                description = "Pauta: fechamento do trimestre.",
                calendarName = "Trabalho",
            ),
        )
        assertEquals(4, detail.size)
        assertNotNull(detail[0].sub)
        assertEquals("Sala 2", detail[1].text)
        assertEquals(labels.detailWhere, detail[1].badge)
        assertEquals(AgendaTone.DIM, detail[2].tone)
        assertEquals(AgendaTone.BODY, detail[3].tone)
        assertEquals(labels.detailNotes, detail[3].badge)
    }

    @Test
    fun `a detail without extras is just the when row and opens nothing`() {
        val detail = detail()
        assertEquals(1, detail.size)
        assertNull(detail[0].badge)
        assertTrue(detail.none { it.target != null })
    }

    @Test
    fun `participants and notes are previews that open a page of their own`() {
        val detail = detail(
            event(description = "Pauta longa. ".repeat(40)),
            listOf(attendee("Ana"), attendee("Bruno"), attendee("Carla")),
        )
        val who = detail.single { it.target == AgendaDetailTarget.PARTICIPANTS }
        val notes = detail.single { it.target == AgendaDetailTarget.NOTES }
        // Both stay inside the 3 lines the renderer gives a prose row.
        assertTrue(who.text.length <= AgendaFormatter.PROSE_ROW_COLS)
        assertTrue(notes.text.length <= AgendaFormatter.PROSE_ROW_COLS)
        assertEquals(AgendaTone.BODY, who.tone)
        assertEquals(labels.detailWho, who.badge)
        assertTrue(who.text.startsWith("3"))
        assertTrue(who.text.contains("Ana"))
        // The order the wearer walks with the ring: who, then notes.
        assertEquals(
            listOf(AgendaDetailTarget.PARTICIPANTS, AgendaDetailTarget.NOTES),
            detail.mapNotNull { it.target },
        )
    }

    @Test
    fun `the focused row says so in its own text, not just by colour`() {
        val rows = detail(
            event(description = "Pauta longa. ".repeat(40)),
            listOf(attendee("Ana"), attendee("Bruno")),
        )
        val marked = AgendaFormatter.markFocused(rows, AgendaDetailTarget.NOTES)
        val notes = marked.single { it.target == AgendaDetailTarget.NOTES }
        val who = marked.single { it.target == AgendaDetailTarget.PARTICIPANTS }
        assertTrue(notes.text.startsWith(AgendaFormatter.FOCUS_MARK))
        assertTrue(!who.text.startsWith(AgendaFormatter.FOCUS_MARK))
        // Context rows never take the mark: they open nothing.
        marked.filter { it.target == null }.forEach {
            assertTrue(!it.text.startsWith(AgendaFormatter.FOCUS_MARK))
        }
    }

    @Test
    fun `taking the mark never pushes a preview into a fourth line`() {
        val rows = detail(
            event(description = "Uma descrição bem comprida que ocupa as três linhas todas " +
                "e continua além delas para forçar o corte do preview."),
            (1..12).map { attendee("Participante$it") },
        )
        AgendaFormatter.markFocused(rows, AgendaDetailTarget.PARTICIPANTS)
            .plus(AgendaFormatter.markFocused(rows, AgendaDetailTarget.NOTES))
            .filter { it.target != null }
            .forEach {
                assertTrue(
                    "row would be clipped: ${it.text}",
                    AgendaFormatter.renderedLineCount(it.text) <= AgendaFormatter.PROSE_ROW_LINES,
                )
            }
    }

    @Test
    fun `the detail footer names what a tap opens`() {
        assertTrue(
            AgendaFormatter.detailFooter(AgendaDetailTarget.PARTICIPANTS, labels)
                .contains(labels.participants),
        )
        assertTrue(
            AgendaFormatter.detailFooter(AgendaDetailTarget.NOTES, labels).contains(labels.notes),
        )
        // Nothing to open: the footer goes back to saying how to leave.
        assertEquals(labels.detailFooter, AgendaFormatter.detailFooter(null, labels))
    }

    @Test
    fun `an event nobody was invited to has no participants row`() {
        assertTrue(detail(event(description = "só notas")).none {
            it.target == AgendaDetailTarget.PARTICIPANTS
        })
    }

    @Test
    fun `a participant row carries the RSVP and marks the organizer`() {
        val rows = AgendaFormatter.participantRows(
            listOf(
                attendee("Carla", organizer = true),
                attendee("Diego", rsvp = AgendaRsvp.DECLINED),
                attendee("Elisa", rsvp = AgendaRsvp.TENTATIVE),
            ),
            labels,
        )
        assertEquals(labels.rsvpAccepted, rows[0].trail.single())
        assertTrue(rows[0].sub!!.startsWith(labels.organizer))
        assertEquals(labels.rsvpDeclined, rows[1].trail.single())
        // Someone who said no is present but not competing for attention.
        assertEquals(AgendaTone.DIM, rows[1].tone)
        assertEquals(labels.rsvpTentative, rows[2].trail.single())
        rows.forEach { assertTrue(it.text.length <= AgendaFormatter.titleColumns(it.trail)) }
    }

    @Test
    fun `an invitation carrying only an address gets a readable name`() {
        // What the provider actually holds for most guests: no display name.
        val row = AgendaFormatter.participantRows(
            listOf(
                AgendaAttendee(
                    name = "ana.ribeiro@example.com",
                    email = "ana.ribeiro@example.com",
                    rsvp = AgendaRsvp.PENDING,
                    organizer = false,
                    self = false,
                ),
            ),
            labels,
        ).single()
        assertEquals("Ana Ribeiro", row.text)
        // The address is still there, one line down, where there is room for it.
        assertEquals("ana.ribeiro@example.com", row.sub)
    }

    @Test
    fun `an attendee with neither name nor address still renders a row`() {
        val row = AgendaFormatter.participantRows(
            listOf(AgendaAttendee("fulano@example.com", null, AgendaRsvp.PENDING, false, false)),
            labels,
        ).single()
        assertEquals("Fulano", row.text)
        assertNull(row.sub)
    }

    @Test
    fun `the preview uses first names, not addresses`() {
        val who = detail(
            event(),
            listOf(
                AgendaAttendee("bruno.dias@example.com", "bruno.dias@example.com", AgendaRsvp.ACCEPTED, true, false),
                attendee("Ana Paula"),
            ),
        ).single { it.target == AgendaDetailTarget.PARTICIPANTS }
        assertTrue(who.text.contains("Bruno"))
        assertTrue(who.text.contains("Ana"))
        assertTrue(!who.text.contains("@"))
    }

    @Test
    fun `the description becomes reader segments, one per paragraph`() {
        val description = "First paragraph, short.\n\nSecond paragraph, also short.\n\nThird."
        val segments = AgendaFormatter.readerSegments(description, labels)
        assertEquals(3, segments.size)
        assertTrue(segments.all { it.kind == AgendaSegmentKind.PROSE })
        assertEquals("First paragraph, short.", segments[0].text)
        assertEquals("Third.", segments[2].text)
    }

    @Test
    fun `the reader keeps the paragraphs a calendar sends as line breaks`() {
        val raw = "Agenda<br /><br />Item one<br />Item two"
        val segments = AgendaFormatter.readerSegments(raw, labels)
        assertEquals(listOf("Agenda", "Item one", "Item two"), segments.map { it.text })
    }

    @Test
    fun `a description with nothing readable produces no segments`() {
        assertTrue(AgendaFormatter.readerSegments(null, labels).isEmpty())
        assertTrue(AgendaFormatter.readerSegments("   ", labels).isEmpty())
        assertTrue(AgendaFormatter.readerSegments("<br /><br /> -::~:~::~ ", labels).isEmpty())
    }

    @Test
    fun `nothing is clipped to three lines any more — a long paragraph stays whole`() {
        val long = "palavra ".repeat(400).trim()
        val segments = AgendaFormatter.readerSegments(long, labels)
        assertEquals(1, segments.size)
        assertEquals(long, segments.single().text)
        // Far past what a prose row could ever draw.
        assertTrue(segments.single().text.length > AgendaFormatter.PROSE_ROW_COLS * 10)
    }

    @Test
    fun `a paragraph past the per-segment cap is split instead of throwing`() {
        val monster = "x".repeat(9_000)
        val segments = AgendaFormatter.readerSegments(monster, labels)
        assertTrue(segments.size >= 3)
        assertTrue(segments.all { it.text.length <= 4_096 })
        assertEquals(monster.length, segments.filter { it.kind == AgendaSegmentKind.PROSE }
            .sumOf { it.text.length })
    }

    private fun payloadBytes(segments: List<AgendaSegment>, shellBytes: Int): Int =
        shellBytes + segments.sumOf { AgendaFormatter.segmentByteCost(it.text) }

    @Test
    fun `a link without the data plane gets a document the control channel can carry`() {
        val long = ("A paragraph of ordinary length that a meeting description would carry. ")
            .repeat(80)
        val shell = 300
        val full = AgendaFormatter.readerSegments(
            long,
            labels,
            AgendaFormatter.readerBudget(shell, dataPlaneUp = true),
        )
        val bounded = AgendaFormatter.readerSegments(
            long,
            labels,
            AgendaFormatter.readerBudget(shell, dataPlaneUp = false),
        )
        assertTrue(payloadBytes(bounded, shell) <= AgendaFormatter.CXR_SAFE_BYTES)
        assertTrue(bounded.sumOf { it.text.length } < full.sumOf { it.text.length })
        // And it says the rest was left out rather than just stopping.
        assertEquals(AgendaSegmentKind.ASIDE, bounded.last().kind)
    }

    @Test
    fun `an accented description at the character cap still fits the 64 KiB payload`() {
        // The bug this pins: 40,000 characters is inside the model's char cap and
        // ~80 KB of UTF-8 once every character is accented, and `sendSurface`
        // answers INVALID_PAYLOAD — the wearer just stays on the previous screen.
        val accented = ("Reunião de acompanhamento com decisões pendentes e ações. ")
            .repeat(1_200)
        assertTrue(accented.length > 40_000)
        val shell = 400
        val segments = AgendaFormatter.readerSegments(
            accented,
            labels,
            AgendaFormatter.readerBudget(shell, dataPlaneUp = true),
        )
        val bytes = payloadBytes(segments, shell)
        assertTrue("payload was $bytes bytes", bytes <= AgendaFormatter.MAX_PAYLOAD_BYTES)
        assertTrue(segments.sumOf { it.text.length } <= 40_000)
        assertEquals(AgendaSegmentKind.ASIDE, segments.last().kind)
    }

    @Test
    fun `a single huge paragraph still delivers its beginning, not just an apology`() {
        val one = ("Uma frase comum de descrição de reunião, com acentuação. ").repeat(60)
        val segments = AgendaFormatter.readerSegments(
            one, labels, AgendaFormatter.readerBudget(300, dataPlaneUp = false),
        )
        // Prose first, notice last — never the notice alone.
        assertEquals(AgendaSegmentKind.PROSE, segments.first().kind)
        assertTrue(segments.first().text.length > 200)
        assertTrue(one.startsWith(segments.first().text.take(40)))
        assertEquals(AgendaSegmentKind.ASIDE, segments.last().kind)
        assertTrue(payloadBytes(segments, 300) <= AgendaFormatter.CXR_SAFE_BYTES)
    }

    @Test
    fun `a heavy shell shrinks the document instead of overflowing the link`() {
        val long = ("Uma frase comum de descrição de reunião, com acentuação. ").repeat(60)
        val small = AgendaFormatter.readerSegments(
            long, labels, AgendaFormatter.readerBudget(200, dataPlaneUp = false),
        )
        val heavy = AgendaFormatter.readerSegments(
            long, labels, AgendaFormatter.readerBudget(1_200, dataPlaneUp = false),
        )
        assertTrue(
            heavy.sumOf { it.text.length } < small.sumOf { it.text.length },
        )
        assertTrue(payloadBytes(heavy, 1_200) <= AgendaFormatter.CXR_SAFE_BYTES)
    }

    @Test
    fun `escaping is counted, not guessed`() {
        assertEquals(4, AgendaFormatter.jsonByteCost("abcd"))
        // Accented characters are two bytes, a quote is escaped to two.
        assertEquals(2, AgendaFormatter.jsonByteCost("ç"))
        assertEquals(2, AgendaFormatter.jsonByteCost("\""))
        assertEquals(6, AgendaFormatter.jsonByteCost("\n"))
    }

    @Test
    fun `the closing notice always has a slot left for it`() {
        // 240 short paragraphs would fill every segment slot; the notice must
        // still fit, or a cut document loses its marker entirely.
        val many = (1..400).joinToString("\n\n") { "Item $it." }
        val segments = AgendaFormatter.readerSegments(
            many, labels, AgendaFormatter.readerBudget(300, dataPlaneUp = true),
        )
        assertTrue(segments.size <= 240)
        assertEquals(AgendaSegmentKind.ASIDE, segments.last().kind)
        assertEquals(labels.readerTruncated, segments.last().text)
    }

    @Test
    fun `a description past the reader total says the rest did not fit`() {
        // The SDK model throws above 40,000 characters; we must stay under it.
        val huge = ("paragraph " + "y".repeat(3_000) + "\n\n").repeat(20)
        val segments = AgendaFormatter.readerSegments(huge, labels)
        assertTrue(segments.sumOf { it.text.length } <= 40_000)
        assertTrue(segments.size <= 240)
        assertEquals(AgendaSegmentKind.ASIDE, segments.last().kind)
        assertEquals(labels.readerTruncated, segments.last().text)
    }

    @Test
    fun `a calendar description is HTML and never reaches the HUD as markup`() {
        // Shortened from a real Google Calendar invitation.
        val raw = "-::~:~::~:~:~:~:~:~:~:~::~:~::-<br />Participar Roam Meeting" +
            "<br /><a href=\"https://ro.am/r/x\" target=\"_blank\">https://ro.am/r/x</a>" +
            "<br />Host: Ana &amp; Bruno&#39;s team&nbsp;&lt;equipe&gt;"
        val text = AgendaFormatter.plainText(raw)
        assertTrue("still markup: $text", !text.contains("<br"))
        assertTrue("still markup: $text", !text.contains("<a "))
        assertTrue("still markup: $text", !text.contains("</"))
        assertTrue(!text.contains("&amp;"))
        assertTrue(!text.contains("&nbsp;"))
        assertTrue(text.contains("Ana & Bruno's team"))
        // A decoded entity may legitimately produce an angle bracket; that is text,
        // not markup, and it must survive.
        assertTrue(text.contains("<equipe>"))
        // The decorative rule is noise that would eat a whole page.
        assertTrue("divider survived: $text", !text.contains("~:~"))
        assertTrue(text.startsWith("Participar"))
    }

    @Test
    fun `the notes preview is cleaned too, not just the page`() {
        val row = detail(event(description = "<br /><b>Pauta</b>: fechamento"))
            .single { it.target == AgendaDetailTarget.NOTES }
        assertEquals("Pauta: fechamento", row.text)
    }

    @Test
    fun `a description that is only markup opens nothing`() {
        assertTrue(AgendaFormatter.readerSegments("<br /><br /> -::~:~::~ ", labels).isEmpty())
        assertTrue(AgendaFormatter.plainText("<div></div>").isEmpty())
    }

    @Test
    fun `the line counter matches how the HUD wraps`() {
        assertEquals(1, AgendaFormatter.renderedLineCount("uma linha curta"))
        assertEquals(1, AgendaFormatter.renderedLineCount("x".repeat(29)))
        assertEquals(2, AgendaFormatter.renderedLineCount("x".repeat(30)))
        assertEquals(2, AgendaFormatter.renderedLineCount("palavra ".repeat(5).trim()))
    }

    @Test
    fun `a URL is counted the way the renderer breaks it, not at the column edge`() {
        // Measured on the device: this link alone fills three lines, because the
        // breaker cuts at the slashes and leaves the previous line short.
        val url = "https://ro.am/r/#/d/AAAAAAAAAAAAAAAAAAAAAA/_bbbbbbbb-cccccccccccc"
        assertEquals(3, AgendaFormatter.renderedLineCount(url))
        // Which is why the preview row stops before it rather than clipping.
        val preview = detail(event(description = "$url e mais texto depois"))
            .single { it.target == AgendaDetailTarget.NOTES }
        assertTrue(AgendaFormatter.renderedLineCount(preview.text) <= AgendaFormatter.PROSE_ROW_LINES)
    }



    @Test
    fun `prose is collapsed to one paragraph and bounded`() {
        val detail = detail(
            event(description = "linha 1\n\n   linha 2\t\tlinha 3 " + "x".repeat(500)),
        )
        val prose = detail.first { it.tone == AgendaTone.BODY }
        assertTrue(prose.text.length <= 220)
        assertTrue(!prose.text.contains("\n"))
        assertTrue(prose.text.startsWith("linha 1 linha 2 linha 3"))
    }

    @Test
    fun `the footer states the position in the whole agenda`() {
        assertEquals("3/40 - ${labels.listFooter}", AgendaFormatter.listFooter(2, 40, labels))
        assertEquals(labels.listFooter, AgendaFormatter.listFooter(0, 0, labels))
    }

    @Test
    fun `a full page of rows stays well under the 3 KiB transport cliff`() {
        val many = (0 until 40).map {
            event(
                id = "$it",
                title = "Reunião de alinhamento semanal $it",
                location = "Sala de reuniões $it",
            )
        }
        val page = AgendaFormatter.page(
            AgendaFormatter.listRows(many, now, zone, locale, labels),
            0,
        )
        val payloadBytes = page.rows.sumOf {
            it.text.length + (it.sub?.length ?: 0) + it.trail.sumOf(String::length) +
                JSON_ROW_OVERHEAD
        }
        assertTrue("estimated payload $payloadBytes B", payloadBytes < 2_300)
    }

    private companion object {
        /** Keys, quotes, braces and the tone/selected fields around one row. */
        const val JSON_ROW_OVERHEAD = 60
    }
}
