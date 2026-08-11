# Lume for Rokid Nexus

**Glanceable RSVP reading on Rokid AR glasses — as a Nexus plugin.**

This is the [Rokid Nexus](https://github.com/Anezium/Rokid-Nexus) port of
[Lume](https://github.com/beyondlevi/lume), the glanceable speed reader for
Rokid glasses. It keeps the reading experience — one word at a time (RSVP) with
adaptive pacing, an on-glasses library, speed control, pause and sentence
stepping, per-document progress — but ships as a single **headless phone APK**
that the permanent Nexus hub renders on the HUD. There is no separate glasses
APK and no Bluetooth plumbing to maintain: the hub owns the link and the
rendering.

## What a Nexus plugin is

Install a phone app, get a glasses app. One hub app lives on the glasses and
renders every plugin's declarative surfaces on the HUD; each plugin is an
ordinary, headless phone APK that talks to the hub over a local bus. Lume is
dormant until you open it from the glasses launcher, declares only the
`surfaces` capability, and is approved per-plugin by the user in Plugin access.

## How the RSVP reader maps onto Nexus

The original streamed word windows over Bluetooth and ticked each word on the
glasses. Nexus offers a **time-synced surface** (`NexusTimedLines`) that the
glasses hub plays on its own clock from a playback anchor — the exact primitive
RSVP needs. So Lume:

- Reuses the original `RsvpEngine` pacing (long words, clause/sentence endings
  and paragraph breaks hold proportionally longer).
- Accumulates those per-word delays into an absolute **timeline** and hands the
  hub a window of `(timeMs, word)` lines. The hub plays the stream; the phone
  never streams word-by-word over the radio.
- Drives **play/pause, speed and sentence-stepping** by updating the surface's
  playback anchor (position + playing flag), and derives the current word from
  the anchor clock to persist reading progress — no back-channel needed.
- Pages the timeline in windows (≤ 2 000 lines / ≤ 64 KiB per surface) so long
  documents stream seamlessly, the Nexus-native equivalent of the original's
  word-window buffering.

## Ring-first controls (R08 Access Bridge)

Every surface is a one-axis linear flow, fully operable by the R08 smart ring,
mirroring the original touchpad gestures:

| Ring verb | Library | Reader (playing) | Reader (paused) |
|---|---|---|---|
| NEXT / PREV | Move selection | Speed +/- | Sentence forward / back |
| SELECT (tap) | Open document | Pause | Resume |
| BACK (double-tap) | Exit plugin | Back to library | Back to library |

## Library on the phone

The settings screen (built on the NexusUi kit) is the library host: import PDF
or TXT files, paste text, set the default reading speed and UI language
(English / Português), and browse documents with live progress. Lume is also a
native **share target** — share text or a PDF/TXT from any app straight into
the library. All text extraction (PdfBox-Android) stays on-device.

## What did not port (and why)

- **Intra-word ORP letter highlight** — the hub renders whole rows; a plugin
  cannot style one letter inside a word. The focal-letter logic is retained in
  `RsvpEngine` for parity and future use.
- **Hybrid CXR/BLE/SPP transport, reconnection, hibernate, see-through HUD
  discipline** — the Nexus hub owns the glasses link and all rendering, so this
  entire layer is intentionally gone.

## Build

Headless, JDK 17 + Android SDK 36. The SDK resolves from JitPack.

```bash
./gradlew :app:testDebugUnitTest   # RSVP timeline + ring-navigability state machines
./gradlew :app:assembleDebug       # debug APK -> app/build/outputs/apk/debug/
```

## Install (on hardware)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then approve Lume's `surfaces` capability in **Rokid Nexus → Settings → Plugin
access**, and open **Lume** from the glasses launcher.

## License

MIT — see [LICENSE](LICENSE). Ported from the original Lume by the same author.
