# Changelog

## 1.1.1 — 2026-08-12

Bounded the reader by the two ceilings the platform actually measures, after the
Store's source review raised the same class of finding on another plugin's reader
port.

- **The document is budgeted in serialized bytes, in both transport modes.** It
  used to count characters only, and drop the budget entirely once the SPP data
  plane was up — but SPP does not relax the SDK's **64 KiB payload ceiling**:
  40,000 characters is inside the model's character cap and up to ~80 KB of
  accented pt-BR or CJK text, so `showReader` answered `INVALID_PAYLOAD` and the
  wearer simply stayed on the previous screen. JSON escaping is counted too.
- **The shell is measured, not assumed.** Title, subtitle, footer and contentKey
  are weighed in UTF-8 before the segments get their budget, so a long accented
  subtitle shrinks the document instead of overflowing the link.
- **A document that had to be cut always delivers its beginning.** A paragraph
  that does not fit now contributes its longest fitting prefix (backed off to a
  word boundary) plus the closing notice, instead of the notice alone.
- A segment slot, its characters and its bytes stay reserved for that notice, so
  a description with more than 240 paragraphs cannot silently lose its marker.

## 1.1.0 — 2026-08-12

- **The description is a real document now.** Notes used to be a card the plugin
  paged by hand: three wrapped lines per row, four rows per page, and every page
  break computed against measured HUD geometry. It is now a native **reader
  surface** — the glasses own the wrapping and the scrolling, there is no line
  clamp, and up to 40,000 characters of invitation text arrive whole. Long
  paragraphs are no longer cut into blocks; a paragraph is a paragraph.
- The reader opens at the **first** line (`anchor = TOP`, glasses hub 1.4.3+),
  which is what a document wants — a chat wants the last line, a meeting
  description does not.
- Scrolling is renderer-owned: the ring's forward/back are consumed by the hub
  inside the reader and never reach the plugin, so the notes view no longer
  carries a page cursor at all. A tap or a double tap still return here.
- Built against bus-client `sdk-v0.15.0`.

## 1.0.1 — 2026-08-07

- **Language is now explicit and switchable.** The plugin always shipped English
  as its default with a Portuguese (pt-BR) translation on top, so a phone set to
  another locale already saw English — but nothing said so, and nothing let a
  wearer choose. It now declares a `localeConfig`, which puts Agenda in Android's
  per-app language picker (Settings › Apps › Agenda › Language): English or
  Portuguese, independently of the phone's language. English remains the default
  and the fallback for every other locale.

## 1.0.0 — 2026-08-07

First Store release. Your calendar on the glasses HUD, fully operable with the
R08 ring.

- **Agenda list** — the next 1 to 30 days of the phone's calendars: title, start
  time on the right (`agora` while it runs, `dia todo` for a full-day entry), and
  the day, the end and the location underneath. Running and imminent events take
  the brightest row tone; later days are dimmed; all-day and multi-day entries
  sort after the day's real commitments. The same appointment mirrored into two
  calendars is collapsed into one row.
- **Event detail** — day and time range, location, source calendar, plus two
  selectable rows: the guest list and the full description. The row in focus
  carries a `›` mark and the footer names what a tap opens.
- **Participants** — one row per guest, organiser first, RSVP on the right,
  declined guests dimmed; invitations that carry only an address get a readable
  name.
- **Notes** — the whole description as a paged reader, with the calendar's HTML
  and its decorative rules stripped.
- **Settings on the phone** — calendar permission, horizon (1/3/7/14/30 days),
  all-day events, hiding declined events, and which calendars to include.
- Reads the phone's calendar provider only, read-only, and declares a single
  capability: `surfaces`. No network, no account of its own, nothing leaves the
  phone.

Walked end to end on RG-glasses (hub 1.2.7) with the R08 axis: NEXT/PREV,
SELECT, BACK through every view.

## 0.4.0 — 2026-08-07

- **The focused row says which one it is.** The hub draws no selection rail on a
  prose row — `bodyRow` only swaps the text colour — so "participants" and
  "notes" stacked on the detail were nearly indistinguishable, and a tap felt
  like a coin flip. The row in focus now carries a `›` mark in its own text, and
  the footer names what a tap opens: `tap to open: Participants`.
- A preview is cut leaving room for the mark, so taking focus never pushes it
  into a fourth line (which the renderer would clip).

## 0.3.4 — 2026-08-07

Participants and the full description, both reachable with the ring.

- **Participants.** The detail now reads the event's guest list from the calendar
  provider (`CalendarContract.Attendees`, still read-only): a preview row of at
  most 3 lines ("16 people - Ana, Bruno, ...") that opens the full list, one
  row per guest, organiser first, RSVP on the right, declined guests dimmed. An
  invitation that carries only an address gets a readable name
  (`ana.ribeiro@...` -> "Ana Ribeiro") with the address on the sub line.
- **Notes.** The description is no longer three lines and a silent truncation: the
  preview opens a paged reader, turned with the ring, `page 1/3` in the footer.
- Both preview rows are **selectable**; everything else on the detail is context.
  The axis walks them, a tap opens the page, BACK returns one step at a time. A
  detail with nothing to open keeps walking the events, so the ring is never inert.
- Calendar descriptions are HTML, and it never reaches the glasses now: `<br />`,
  anchor tags and entities are stripped and the decorative `-::~:~::~` rule that
  ate a whole page is dropped. On a real invitation this went from 4 pages to 3
  of actual content.
- **Prose rows are sized by the lines the HUD really draws**, not by a character
  count. The renderer breaks a URL at its slashes, so a row measured in characters
  overflowed 3 lines and lost its tail invisibly. Verified on device: no row is
  ellipsised any more, and page 2 resumes exactly where page 1 stopped.
- A guest list that arrives after the first render (the provider query is async)
  now takes the focus of an untouched cursor — before this, opening an event and
  tapping landed on Notes because that was the only row at first render.

## 0.2.1 — 2026-08-07

- Collapse mirrored duplicates: the same appointment synced into two calendars
  (a work account plus its Outlook mirror) is one row, keeping the copy that
  carries the location/notes. Device-verified: 32 rows -> 28 on a real agenda.

## 0.2.0 — 2026-08-07

Everything here came out of walking v0.1 on real RG-glasses.

- **The event time is now visible.** It rode in the row `badge`, which the hub
  only draws on prose rows — a list row is `text` + `trail` + `sub`, so the time
  was silently dropped. It moved to `trail`, and the title is now sized from the
  trail width instead of a fixed cap.
- **Order by what is next.** All-day and multi-day entries ("Férias", "Ausente")
  are background: sorted by start alone they held the top of the list for weeks
  and pushed today's meetings off screen. They now sort after the day's real
  commitments and never take the ALERT tone.
- **No more "até 00:00".** An event that started on an earlier day leads with
  when it ends, and an end on another day names that day; a midnight end resolves
  to the day it really covers. An evening running to midnight keeps the clock.
- The detail view states when the event runs (`2/32 · 10:30 - 12:30`) instead of
  repeating the list's input hint, and labels location/notes rows.
- A truncated agenda shows its count as a floor (`120+`).
- Settings: toggling a row no longer rebuilds the screen, so a long calendar list
  stays exactly where it was scrolled; unavoidable rebuilds keep the offset.
- Turning every calendar off now means an empty agenda instead of silently
  resetting to "all calendars".

## 0.1.0 — 2026-08-06

First build.

- List surface: the next N days of the phone's calendar as HUD rows (title,
  start-time badge, day/end/location sub line), ongoing and imminent events
  raised to the ALERT row tone, later days dimmed.
- Detail surface: day, time range, location, source calendar, description.
- Full R08 one-axis navigation (NEXT / PREV / SELECT / BACK), proven by JVM
  unit tests on `AgendaState`.
- Settings: calendar permission, horizon (1/3/7/14/30 days), all-day events,
  hide declined, per-calendar selection, uninstall row.
- Capabilities: `surfaces` only. Data source: `CalendarContract`, read-only.
