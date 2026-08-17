# Changelog

## 0.6.5

- Match the notification-access guide to the exact labels exposed by Pixel Android 16
  Settings: Notifications, Conversations, Real-time, Silent, and See all apps.

## 0.6.4

- Correct the notification-access guide: Android can filter a listener by notification
  category and by specific source apps.
- Open Taxi Plate's system notification-listener detail page directly, with a fallback to
  the general listener list.

## 0.6.3

- Preview every package requested by an imported adapter bundle and require the user to
  choose which packages Taxi Plate may parse.
- Apply package choices before reading notification extras or recording diagnostics,
  including matching notifications delivered from work and private profiles.
- Add an in-app guide to Android's system notification-access controls and profile limits.
- Run imported regex rules with RE2/J and cap matching input to prevent regex denial of
  service.
- Add password-encrypted settings export and import for migrations between debug and
  store-signed installs. The backup excludes rides, history, logs, and system permissions.

## 0.6.2

- Replace test fixtures with clearly synthetic ride data.
- Resolve the Android SDK from the current user's local application-data directory.
- Build against the latest published Nexus SDK (`sdk-v0.15.0`).

## 0.6.1

- Add the phone-only Taxi Plate plugin for Rokid Nexus.
- Parse configurable ride-provider notifications and keep the current ride state.
- Show ride details as a Nexus card and maintain compact plate or arrival-time pins.
- Add localized settings, notification diagnostics, and adapter import/export.
