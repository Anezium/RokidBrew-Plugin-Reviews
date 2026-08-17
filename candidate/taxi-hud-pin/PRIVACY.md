# Privacy

Taxi Plate processes notifications only on the Android phone where it is installed.
Notification access is granted explicitly in Android settings and can be revoked at
any time.

Taxi Plate keeps a separate allow switch for each configured package name. The listener
checks that switch before reading notification text, parsing it, or adding it to local
diagnostics. Imported adapter bundles display all requested packages and require explicit
confirmation; newly requested packages are disabled by default. The same package choice
applies across personal, work, and private profiles, but only to notifications Android
actually delivers to the personal-profile listener.

Android's notification-listener detail screen can grant or revoke access, choose which
notification categories are exposed (Notifications, Conversations, Real-time, and Silent
in Pixel Settings), and use **See all apps** to restrict individual source apps. These system
controls are the primary OS-level boundary; Taxi Plate's own package switches add another
check before parsing or diagnostics. Work-profile policy and Private Space state can
further prevent cross-profile delivery.

The plugin extracts only the ride fields needed for its card and pin. Current ride
state, adapter configuration, and recent diagnostics stay in the plugin's private
app storage. Taxi Plate does not upload notification contents or ride data to a
server. Diagnostic export happens only after an explicit user action and may contain
sensitive ride details; the exported file is controlled by the user.

The settings screen can create a portable backup after an explicit user action. It contains
imported adapter definitions, adapter and package choices, the automatic trip-pin
preference, and language. It excludes current rides, ride history, diagnostics, and system
permissions. The file is encrypted with AES-256-GCM under a user-provided password derived
with PBKDF2-HMAC-SHA256; the password is not stored by Taxi Plate. Import replaces only the
settings represented in the backup.

Imported regular expressions run through the linear-time RE2/J engine, and the text passed
to matching is capped at 8192 characters per notification.

Removing the plugin deletes its private Android app data. Imported adapter files and
diagnostic exports stored outside the app remain under the user's control.
