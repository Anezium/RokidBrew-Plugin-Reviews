# OTPs

OTPs is a headless Rokid Nexus phone plugin. It watches notifications after the
wearer explicitly grants Android Notification Access, extracts high-confidence
verification codes, and shows them on the glasses.

- Timed alerts use a Nexus activity with a countdown progress bar and a single
  `Close` action.
- Timer-off alerts use the same activity without a deadline and close only from
  `Close` or when replaced.
- Opening OTPs from the Nexus glasses launcher shows the newest ten entries as
  `App: Code`.
- The phone settings screen controls alert delivery, timeout, notification
access, history clearing, and uninstall.

The settings screen also provides password-encrypted **Export settings** and
**Import settings** actions for alert enablement, auto close, and duration. Stored
verification-code history is intentionally excluded from portable backups.

All notification text and extracted codes are processed locally. The plugin has no
Internet permission and keeps at most ten entries in private app storage. See
[`PRIVACY.md`](PRIVACY.md) for the complete data-handling summary.

## Build and test

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug packageDebugApk
```

The APK is written to `build/outputs/otps-phone-debug.apk`. The standalone
project consumes the public Nexus SDK `bus-client:sdk-v0.15.0` from JitPack.

## Release signing

Store releases are built only from a clean tagged revision and signed with the
project's permanent PKCS12 certificate. Configure `NEXUS_RELEASE_KEYSTORE`,
`NEXUS_RELEASE_KEYSTORE_PASSWORD`, and `NEXUS_RELEASE_KEY_ALIAS` in the release
environment. Never commit the keystore or its passwords.
