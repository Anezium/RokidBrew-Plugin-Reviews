# Taxi Plate for Rokid Nexus

Taxi Plate is a single phone-side [Rokid Nexus](https://github.com/Anezium/Rokid-Nexus)
plugin. It reads notifications through configuration-driven adapters, keeps one current
ride state across multiple enabled providers, and publishes a native Nexus PIN. Rokid Nexus is the only app that owns the
Bluetooth/CXR link and the only permanent renderer installed on the glasses.

There is no Taxi Plate APK for the glasses, no embedded helper, no Hi Rokid authorization,
and no Bluetooth permission in this project.

## Runtime flow

1. Android delivers notifications from packages enabled in the adapter repository.
2. `NotificationAdapterEngine` applies the matching JSON rules and `TaxiCoordinator`
   persists the resulting ride state.
3. `NexusTaxiHudTransport` briefly connects a `NexusPluginClient`, sends one
   `showPin` or `hidePin`, and immediately disconnects.
4. Nexus keeps the text PIN in the top-right corner independently of the plugin process.
5. The Nexus phone and glasses hubs own transport, arbitration, placement, and rendering.

Opening Taxi Plate from the Nexus launcher shows the current ride status. Before pickup,
the action clears the Android notification and plate PIN. During a trip, the card keeps the
remaining travel time visible even when no PIN is showing; one center tap creates the small
arrival-timer PIN. Back only closes the launcher surface and does not dismiss the ride.

The medium PIN contains the plate, vehicle, and ETA/waiting status. It survives launcher
and surface changes, sleeping glasses, and the Taxi Plate process disconnecting; each update
refreshes its 30-minute TTL. The phone also shows one silent, dismissible Android
notification for the current ride. At trip start the plate PIN is removed. The arrival
timer PIN can be shown manually or automatically through the plugin setting. It uses the
localized `Until arrival / X min` (or hours plus minutes) layout and refreshes on minute
boundaries. Swiping its phone notification hides only that PIN; trip progress remains in
the launcher card. The trip-end notification clears the timer automatically.

## Notification adapters

The app ships with an enabled Yandex Go adapter as
`app/src/main/assets/adapters/builtin_notification_adapters.json`; it is data, not a
hard-coded parser. Settings opened from Nexus list every adapter and allow each one to be
enabled or disabled independently. **Import JSON** uses Android's document picker and
merges adapters by `id`, so a bundle can add Uber, a local taxi app, or replace the
built-in definition. **Reset imported adapters** returns to the built-in set.

Bundles use `schemaVersion: 1`. Each adapter declares `id`, `displayName`, `packages`,
`eventRules`, `fieldRules`, optional truncation rules, activation requirements, and
`pinTtlMs`. Android countdown/`when` metadata is also captured for in-trip ETA. Field rules
can extract `TRIP_DURATION` text when a provider exposes duration in text instead. Rules
select a regex capture `group` and a closed list of transforms:
`TRIM`, `UPPERCASE`, `REMOVE_WHITESPACE`, `NORMALIZE_PLATE_LETTERS`,
`NORMALIZE_RANGE`, and `CLEAN_VEHICLE`. See
`docs/notification-adapter-example.json` for a complete custom-provider example.

Imports are limited to 256 KiB, 32 adapters, and bounded rule counts/pattern sizes. The
entire bundle is validated before it replaces persisted configuration; invalid imports
leave the working configuration unchanged.

## Setup

1. Install and onboard a current Rokid Nexus release with PIN support,
   including its one glasses hub.
2. Install the Taxi Plate plugin APK on the phone.
3. In Nexus, approve the `surfaces` capability for Taxi Plate.
4. Open Taxi Plate settings from Nexus and grant Android notification-listener access.
5. Grant the separate Android permission that lets Taxi Plate post its own notification.

The APK has no launcher activity. Nexus opens its exported settings activity explicitly.

## Build and verify

```powershell
.\scripts\taxi-hud-regression.ps1
```

The build consumes the public Nexus SDK `bus-client:sdk-v0.15.0` from JitPack.
The regression script runs unit tests, Android lint, and the debug APK build.

The single output is:

- `build\outputs\taxi-hud\Taxi-Plate-debug.apk`

The APK identity is `com.havoc.rokid.plugin.taxihudpin` and its Nexus plugin id is
`taxi-hud-pin`, so it installs beside the existing `dev.havoc.taxihud.phone` app.

To install it for the personal Android profile (user 0) on the only connected phone:

```powershell
.\scripts\taxi-hud-regression.ps1 -Install
```

Raw notification diagnostics remain local until the user explicitly exports JSONL; exported
data may contain sensitive ride details.

## Release signing

Store releases are built only from a clean tagged revision and signed with the
project's permanent PKCS12 certificate. Configure `NEXUS_RELEASE_KEYSTORE`,
`NEXUS_RELEASE_KEYSTORE_PASSWORD`, and `NEXUS_RELEASE_KEY_ALIAS` in the release
environment. Never commit the keystore or its passwords.
