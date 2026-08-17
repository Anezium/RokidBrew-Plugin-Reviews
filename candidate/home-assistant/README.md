# Home Assistant for Rokid Nexus

Headless phone plugin for Rokid Nexus. The plugin stores Home Assistant credentials, edits pages and context rules, evaluates live values, renders the current page through a typed Nexus HUD card, and executes the action assigned to the selected widget.

This is an unofficial community plugin. It is not affiliated with or endorsed by
the Home Assistant project, Nabu Casa, or Rokid. The Home Assistant name and plugin
icon are kept as the established in-app identity; the same disclosure is shown
inside the plugin settings screen.

There is one active APK:

- `phone` — Nexus plugin service, Nexus-style settings UI, Room, Android Keystore and Home Assistant client;
- `shared` — configuration models, validation and page/focus/context state machines;
- `glasses` — legacy CXR implementation kept as migration history and excluded from Gradle.

Nexus owns the phone-to-glasses link, plugin approval, HUD surface, foreground session, reconnect and normalized input events. The plugin does not use Global Hi Rokid authorization, does not store a Hi Rokid token and does not install a separate glasses APK.

## SDK

- Rokid Nexus plugin API: `3`
- Published SDK: `com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.15.0`
- Android: `minSdk 30`, `targetSdk 36`

The plugin requests only the `surfaces` capability.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :shared:test :phone:testDebugUnitTest :phone:lintDebug :phone:assembleDebug
```

APK:

- `phone/build/outputs/apk/debug/phone-debug.apk`

## Install and first use

1. Install a current Nexus phone and glasses hub release.
2. Install `phone-debug.apk` into the phone's personal profile.
3. Open Nexus → Plugins → Home Assistant and approve `Surfaces` once.
4. Open the plugin settings from Nexus. The phone UI follows the canonical sample-plugin layout: fixed plugin header, phosphor theme, section rows, cards, pill actions and uninstall card.
5. In Connection settings, save the Home Assistant URL and long-lived access token.
6. Edit pages/widgets/context if needed and press `Publish to Nexus HUD`.
7. Launch Home Assistant from the Nexus launcher on the glasses.

New installations start with a neutral guide page. Add your own pages, widgets,
entity IDs, actions, and optional context rules from the phone settings, then
publish the configuration to the Nexus HUD.

Settings provide password-encrypted **Export settings** and **Import settings** actions.
The portable backup includes the Home Assistant URL and long-lived token plus all editable
pages, widgets, actions, value bindings, and context rules. The password is not stored and
cannot be recovered.

## Glasses controls

- Page mode: forward/backward scroll changes pages; tap enters control focus; Back closes the Nexus surface immediately.
- Control mode: scroll cycles through buttons and toggles; tap executes the selected Home Assistant action; Back clears focus and returns to page mode.
- Nexus performs physical-event normalization before delivery. The plugin handles each delivered `ACTION_DOWN` once.

Page 0 is a virtual dynamic slot. Its currently assigned configured page is removed from its ordinary position, so the page appears once and the counter equals the configured page count. If page 0 is visible, context changes remain pending until the wearer leaves it or Nexus closes the session. Every `onNexusOpen`, including a re-entrant open, commits pending context and starts on page 0.

## Security and lifecycle

- The Home Assistant token is protected by Android Keystore and never enters the Nexus descriptor, HUD surface or glasses payload.
- The Home Assistant WebSocket and template loop exist only while a settings screen or Nexus HUD session is active.
- The plugin has no launcher activity, boot receiver, persistent background service or `POST_NOTIFICATIONS` permission.
- Home Assistant actions are resolved from the active published widget and are never retried automatically after an unknown timeout.

## Current limits

- Widgets are projected into structured Nexus card rows: Text, Status, Button, Toggle and Progress.
- The HUD layout is owned by Nexus; plugins cannot freely position Android views on the glasses.
- Jinja is refreshed after state changes and at least every 30 seconds while the plugin is active.
- When Home Assistant is offline, cached values and page navigation remain available while action execution is disabled.

Technical decisions are in [the design](docs/superpowers/specs/2026-07-18-rokid-home-assistant-companion-design.md).

## Release signing

Store releases are built only from a clean tagged revision and signed with the
project's permanent PKCS12 certificate. Configure `NEXUS_RELEASE_KEYSTORE`,
`NEXUS_RELEASE_KEYSTORE_PASSWORD`, and `NEXUS_RELEASE_KEY_ALIAS` in the release
environment. Never commit the keystore or its passwords.
