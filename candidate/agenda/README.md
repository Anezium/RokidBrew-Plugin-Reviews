# Agenda — a Rokid Nexus plugin

Your calendar on the Rokid glasses HUD: the next days as a list you walk with
the R08 ring, and one tap for the details of an event.

A **headless phone APK** for the [Rokid Nexus](https://github.com/Anezium/Rokid-Nexus)
platform. It has no launcher icon: the glasses hub opens it, renders its
surfaces and closes it again. Nothing is installed on the glasses.

## What it shows

- **List** — one row per event: title, the start time on the right (`now` while
  it runs, `all day` for a full-day entry), and a sub line with the day, the end
  and the location. An event that is running, or starts within 15 minutes, is
  raised to the HUD's brightest row tone; days further out are dimmed. All-day
  and multi-day entries are background: they sort after the day's real
  commitments. The same appointment mirrored into two calendars is one row.
- **Detail** — the full day and time range, the location, the source calendar,
  plus two **selectable** preview rows of at most three lines each:
  - **Participants** — "16 people - Ana, Bruno, ..." opens the full guest
    list: one row per person, organiser first, RSVP on the right, declined guests
    dimmed. Invitations that carry only an address get a readable name.
  - **Notes** — opens the whole description as a native **reader surface**: no
    three-line clamp, no pages, up to 40,000 characters, with the calendar's HTML
    and decorative rules stripped. The glasses own the wrapping and the
    scrolling; the document opens at its first line (`anchor = TOP`).

  The row in focus carries a `›` mark and the footer names what a tap opens
  (`tap to open: Participants`) — a prose row gets no selection rail from the
  hub, only a colour change, which is too quiet to navigate by.

## Ring navigation (R08 Access Bridge)

The plugin is operable end to end on the ring's single axis — the four verbs are
all it understands:

Keys: NEXT = `DPAD_RIGHT`/`DPAD_DOWN`, PREV = `DPAD_LEFT`/`DPAD_UP`,
SELECT = `DPAD_CENTER`/`ENTER`, BACK = `KEYCODE_BACK`.

| Verb | List | Detail | Participants | Notes |
|---|---|---|---|---|
| NEXT / PREV | walk the events | walk the openable rows (or the events, when the detail opens nothing) | walk the guests | scroll (consumed by the hub, never reaches the plugin) |
| SELECT | open the detail | open that row's page | — | — |
| BACK | close the plugin | back to the list | back to the detail | back to the detail |

`AgendaState` is a plain Kotlin class, so the navigability contract is asserted
by JVM unit tests rather than by hand on the glasses.

## Where the events come from

The phone's calendar provider (`CalendarContract.Instances`), read-only, with
`READ_CALENDAR`. Every account the phone syncs lands there — Google, Exchange /
Outlook, CalDAV via DAVx5, local calendars — and the provider already expands
recurrence rules over a time window. **No network, no account of its own: the
plugin declares only the `surfaces` capability and nothing leaves the phone.**

Settings (Nexus → Plugin access → Agenda → settings) cover: the permission, the
horizon (1 / 3 / 7 / 14 / 30 days), all-day events, hiding events you declined,
and which calendars to include.

## Platform budgets respected in code

- Rows are paged to `ROWS_PER_PAGE = 12` so a payload stays far below the ~3 KiB
  CXR transport cliff; a page always contains the focused row.
- A list row is drawn as `text` + `trail` + `sub`; a row `badge` is only rendered
  on prose rows, so timing rides in the trail. The title is sized from the trail
  width (~28 columns total, ~0.8 column per trail character plus a gap), because
  a list-row title is one line and is ellipsised, never wrapped.
- Prose rows are cut by the **wrapped lines the HUD draws** (3 lines of ~29
  columns), modelling where the line breaker cuts a long token — a row measured
  in characters loses its tail silently. This applies to the one-row previews;
  the full description goes out as a reader, which has no clamp at all.
- A reader document is budgeted in **serialized bytes**, against the two ceilings
  the platform measures: the SDK rejects a payload past **64 KiB**
  (`INVALID_PAYLOAD`) whatever the transport, and a control-channel-only link
  carries ~3 KiB. Characters are not a proxy for either — 40,000 characters is
  inside the model's cap and ~120 KB of CJK. The title/subtitle/footer/contentKey
  are weighed before the segments get their share, JSON escaping is counted, and
  a document that had to be cut delivers its beginning plus a closing note.
- `contentKey` is keyed on view identity, never built from content (128-char cap).
- The surface is refreshed once a minute while open, and only while open: the
  process is dormant outside `PLUGIN_OPEN` → `PLUGIN_CLOSE`.

## Build

JDK 17 + Android SDK 36 (see the Nexus `nexus-build-and-deploy` recipe). Needs
bus-client `sdk-v0.15.0` and glasses hub **1.4.3+** (the reader's `TOP` anchor).

```bash
sh ./gradlew :app:testDebugUnitTest   # the R08 + formatter contract
sh ./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Install on the **phone** (not the glasses), then approve the plugin in
Nexus → Plugin access before launching it from the glasses launcher:

```bash
adb -s <phone-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

## Layout

| File | Role |
|---|---|
| `AgendaPluginService.kt` | The `NexusPluginService` adapter: lifecycle, input, surfaces |
| `AgendaState.kt` | The one-axis state machine (pure Kotlin, unit-tested) |
| `AgendaFormatter.kt` | Events → HUD rows, the char/line budgets, HTML stripping, paging (pure Kotlin) |
| `CalendarRepository.kt` | The calendar provider queries (agenda, calendars, attendees) |
| `AgendaPrefs.kt` | Horizon, filters, selected calendars |
| `AgendaActivity.kt` | The phone settings screen (NexusUi kit, uninstall row) |

## License

Apache-2.0 — see [LICENSE](LICENSE).
