# Taxi Plate Nexus QA Matrix

## Automated regression

Run from the project root:

```powershell
.\scripts\taxi-hud-regression.ps1
```

The command performs a clean build, runs the phone/plugin unit tests, produces exactly
one named Nexus plugin APK, and prints its SHA-256. Installation is opt-in with
`-Install` and targets only a selected phone.

| Automated area | Required regression evidence |
|---|---|
| Nexus contract | API 3 `surfaces` service, no launcher intent, no Bluetooth permission, no CXR dependency, and one Gradle module. |
| Pin rendering | Medium top-right pin with bounded plate, vehicle, ETA/waiting status, adapter-defined TTL/source, and fire-and-forget delivery. |
| Dismissal | One silent Android notification mirrors active state; swipe dismissal and the launcher tap action both cancel it and hide the pin. |
| Adapter engine | Built-in compatibility, explicit package choices, cross-profile package filtering, RE2/J hostile-pattern safety, bounded input, invalid import rejection, merging, and independent toggles. |
| Ride state | Manual hide, suppression, replacement, restore, countdown scheduling, and expiry. |
| Diagnostics | Structured parser fields, retention, JSONL export, clear, and content URI. |

## Hardware matrix

Complete these checks with Rokid Nexus already onboarded on phone and glasses.

| # | Scenario | Expected result |
|---|---|---|
| 1 | Install Taxi Plate APK | Nexus discovers one headless plugin; no Taxi Plate app is installed on the glasses. |
| 2 | Approve `surfaces` in Nexus | Taxi Plate becomes launchable from the Nexus glasses launcher. |
| 3 | Grant Android notification-listener access | Settings shows listener enabled; no Bluetooth or Hi Rokid prompt appears. |
| 4 | Active notification from any enabled adapter while the plugin is closed | Nexus shows one native top-right pin with source, plate, vehicle, and ETA; Taxi Plate disconnects after the push. |
| 5 | ETA or waiting update | Existing pin is replaced in place without a second Bluetooth owner. |
| 6 | Another Nexus plugin owns the HUD | Taxi pin remains in the global pin slot while the other plugin keeps its interactive surface. |
| 7 | Dismiss the ride from Taxi Plate | Nexus receives `hidePin`; later updates for the same ride stay suppressed. |
| 8 | Vehicle changes or suppression expires | The current/new ride may auto-open again through Nexus. |
| 9 | Receive `Водитель начал поездку` | Overlay shows the countdown status and Nexus hides the ride at the persisted deadline. |
| 10 | Disconnect and reconnect glasses | Nexus restores transport; the plugin sends only the latest persisted ride state. |
| 11 | Open Taxi Plate from the Nexus launcher with an active notification | The only action is `Tap to Clear Notification`; tapping it removes the Android notification and pin. |
| 12 | Open Taxi Plate without an active notification | The clear action is absent. |
| 13 | Swipe away the Taxi Plate Android notification | The saved ride becomes dismissed and Nexus receives `hidePin`. |
| 14 | Send a test widget from plugin settings | A synthetic Android notification and ride pin are shown. |
| 15 | Put glasses to sleep, then post an update | Nexus accepts and holds the pin, then delivers it after glasses reconnect. |
| 16 | Import a valid second-provider JSON bundle | Before saving, Taxi Plate shows every requested package unchecked; only selected packages begin matching. |
| 17 | Disable a package in Taxi Plate and post matching notifications from personal, work, and available private profiles | None of the delivered matching notifications is parsed or written to diagnostics. Re-enabling the package restores processing for every profile Android exposes. |
| 18 | Open **How to limit notification access** | The guide explains app filters, global Android listener access, source-app notification blocking, work-profile policy, and locked Private Space, and its button opens Notification access. |
| 19 | Import malformed JSON, unsupported lookbehind/backreference rules, or an oversized bundle | Import is rejected and all previously working adapters and package choices remain unchanged. |
| 20 | Export diagnostics | JSONL contains raw fields only for allowed packages, plus adapter identity, parser result, and state decisions. |

Record Nexus phone/glasses versions, Android/Rokid software versions, date, and result.
Do not mark hardware checks passed from unit tests alone.
