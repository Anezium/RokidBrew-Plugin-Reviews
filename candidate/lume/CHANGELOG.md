# Changelog

## 1.0.11

- Phone library now shows with a single document. The settings ScrollView no
  longer stretches its content to the viewport (kit `fillViewport` default),
  which — with exactly one library card — inflated the paste card and pushed the
  library list and uninstall row off-screen. They render at any document count.
- On the glasses, BACK inside a book returns to the library instead of exiting
  the plugin (progress is saved first).
- Updated the bus-client SDK to the latest published line (sdk-v0.14.0).

## 1.0.10

- Lowered `minSdk` from 31 to 30. The Nexus platform dropped to API 30 for
  Android 11 support; a minSdk-31 APK cannot be parsed on an API 30 phone, so
  the Store install fails (surfacing as a misleading "downloaded APK does not
  match the registry" error). No functional change otherwise.

## 1.0.9

- Playing view now shows only the active word — the book title no longer appears
  (and no longer marquee-scrolls/resets) at the top. The single-entry timed-lines
  surface uses a zero-width title, which the HUD renders as nothing.

## 1.0.8

- Reader display reworked. While **playing**, the HUD now shows only the active
  word (a single-entry timed-lines surface, so the hub has no previous/next
  lines to draw) — no flanking words to distract. While **paused**, the active
  word is the title and the surrounding sentences (>= 2) render below it as a
  context block; NEXT/PREV still step between sentences.
- Playback is driven word-by-word by a phone-side ticker at the RsvpEngine pace;
  each surface is tiny and rides CXR (no SPP dependency, no windowing).
- Built on the bus-client sdk-v0.2.x line (matching rokid-inbox-nexus).

## 1.0.7

- Bumped the bus-client SDK to the sdk-v0.2.x line (sdk-v0.2.1), matching the
  rokid-inbox-nexus plugin, and moved the build to Java 17.

## 1.0.6

- Icon: declare BOTH the built-in `ICON=bookmark` and
  `ICON_DRAWABLE=@drawable/nexus_glyph_lume`. A custom drawable never reaches
  the glasses launcher (it lives in the phone APK), so v1.0.5's drawable-only
  setup showed the generic grid glyph on the glasses. With both declared the
  built-in renders natively on the glasses today, and a later hub release will
  show the custom glyph on the phone — best of both.

## 1.0.5

- Glasses HUD icon now uses Lume's own RSVP glyph: the descriptor declares
  `ICON_DRAWABLE=@drawable/nexus_glyph_lume` (monochrome, Nexus-tinted) instead
  of the built-in `bookmark`, so the same mark shows on the HUD and the Android
  app icon.
- PDF import is bounded to keep memory predictable: input size capped at 30 MB
  and extraction capped at 800 pages, with a guard that degrades to no-text
  instead of crashing on `OutOfMemoryError`.
- Release built from the tagged commit (commit -> tag -> build -> publish) so
  the published APK verifies against the descriptor.
- Note: bumping the PdfBox-Android Bouncy Castle dependency is deferred as an
  optional follow-up.

## 1.0.4

Maintainer review fixes (Nexus Store).

- Settings header now reads the real `versionName` from the package manager
  instead of a hardcoded string, so it never drifts from the actual version.
- Launcher and monochrome app icon now use Lume's own RSVP glyph everywhere:
  `ic_launcher_foreground` is an `<inset>` of the new monochrome
  `@drawable/nexus_glyph_lume` (was the generic scaffold glyph).

## 1.0.3

- Display no longer dims while reading. During playback the plugin was silent on
  the bus between buffer pages (several seconds with no surface activity), so the
  glasses dimmed mid-read while a paused screen — freshly updated on the pause
  tap — stayed lit. The reader now emits a keep-alive anchor resync every ~1.5 s
  while playing, keeping the hub rendering (and the display awake) throughout a
  read; the resync also corrects any small playback-clock drift.

## 1.0.2

Second round of on-device fixes.

- Reader now streams as a **sliding buffer**. On a CXR-only link the hub rejects
  any JSON surface larger than 3 KiB (it needs the SPP data plane), which is why
  documents past ~100 words never rendered. The reader now sends a small
  ~60-word window with per-window relative timestamps (well under 3 KiB), plays
  it on the glasses clock, and pages the next window in before the read head
  runs out — reading of arbitrary-length documents works without SPP.
- Documents open **paused**; a tap starts playback (was auto-playing on open).
- Removing a document no longer blanks the settings screen: the rebuild is now
  deferred off the click dispatch instead of tearing down the view mid-click.
- Reading progress is persisted on a throttle while playing, and the buffer
  re-anchors to the live position on each page so playback stays seamless.

## 1.0.1

Fixes from first on-device testing.

- Reader now opens. The timed-lines surface is built within a byte budget so a
  large document's window can never exceed the hub's 64 KiB surface ceiling
  (which was silently rejecting the whole surface, leaving the library card on
  screen). The window also pages down automatically for documents with very
  long extracted tokens.
- Surface protocol aligned with the shipped Transit/Lyrics plugins: a single
  surface is shown once per session and updated thereafter, and the
  library-card -> reader transition goes through updateTimedLines.
- BACK behaviour made coherent: a timed-lines surface cannot intercept BACK, so
  (like Lyrics) BACK from the reader now exits the plugin cleanly instead of
  leaving a stuck intermediate state; footers say so.
- Phone library list refreshes reliably: the store re-reads from disk on resume
  and on every rebuild, so documents added via the share target or the file
  picker always appear (and can be removed).
- Opening a document that yields no text now shows a visible message instead of
  silently doing nothing.

## 1.0.0

Initial Rokid Nexus port of Lume (headless phone-side plugin, id `lume`).

- RSVP reader rendered as a time-synced `NexusTimedLines` surface; the glasses
  hub plays the word stream on its own clock from a playback anchor.
- Adaptive pacing ported verbatim from the original `RsvpEngine` (long words,
  clause/sentence endings, paragraph breaks) accumulated into a document
  timeline, paged in windows to respect surface limits.
- Full R08 ring one-axis control: SELECT play/pause, NEXT/PREV speed while
  playing and sentence-step while paused, BACK to library / self-close.
- Phone library on the NexusUi kit: import PDF/TXT, paste text, default speed,
  English/Português, per-document progress, remove.
- Native share target for text and PDF/TXT files (single or multiple).
- Per-document reading progress persisted from the anchor clock.
- Unit tests for the RSVP timeline and the library/reader one-axis state
  machines (ring-navigability proof).
