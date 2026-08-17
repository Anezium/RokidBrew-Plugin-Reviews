# Privacy

OTPs processes Android notifications locally after the user explicitly grants
Notification Access. The plugin looks for high-confidence verification-code patterns
and ignores notifications that do not match its conservative rules.

The app does not declare the Android Internet permission and does not upload
notification text, application names, or extracted codes. It keeps only the newest
ten detected entries in private app storage so they can be reopened from the Nexus
launcher. History can be cleared from plugin settings, and uninstalling the plugin
deletes its private data.

Notification Access can be revoked at any time in Android settings. Disabling alert
delivery stops automatic presentation on the glasses without changing Android's
separate Notification Access grant.

