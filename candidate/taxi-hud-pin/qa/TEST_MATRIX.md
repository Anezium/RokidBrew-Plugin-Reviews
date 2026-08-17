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
| PIN rendering | Medium top-right PIN with bounded plate, vehicle, ETA/waiting status, adapter-defined TTL/source, and fire-and-forget delivery. |
| Dismissal | One silent Android notification mirrors active state; swipe dismissal and the launcher tap action both cancel it and hide the PIN. |
| Adapter engine | Built-in compatibility, custom package matching, ETA/waiting/lifecycle rules, invalid import rejection, merging, and independent toggles. |
| Ride state | Manual hide, suppression, replacement, restore, countdown scheduling, and expiry. |
| Diagnostics | Structured parser fields, retention, JSONL export, clear, and content URI. |

## Hardware matrix

Complete these checks with Rokid Nexus already onboarded on phone and glasses.

| # | Scenario | Expected result |
|---|---|---|
| 1 | Install Taxi Plate APK | Nexus discovers one headless plugin; no Taxi Plate app is installed on the glasses. |
| 2 | Approve `surfaces` in Nexus | Taxi Plate becomes launchable from the Nexus glasses launcher. |
| 3 | Grant Android notification-listener access | Settings shows listener enabled; no Bluetooth or Hi Rokid prompt appears. |
| 4 | Active notification from any enabled adapter while the plugin is closed | Nexus shows one native top-right PIN with source, plate, vehicle, and ETA; Taxi Plate disconnects after the push. |
| 5 | ETA or waiting update | Existing PIN is replaced in place without a second Bluetooth owner. |
| 6 | Another Nexus plugin owns the HUD | Taxi PIN remains in the global pin slot while the other plugin keeps its interactive surface. |
| 7 | Dismiss the ride from Taxi Plate | Nexus receives `hidePin`; later updates for the same ride stay suppressed. |
| 8 | Vehicle changes or suppression expires | The current/new ride may auto-open again through Nexus. |
| 9 | Receive `Водитель начал поездку` | Overlay shows the countdown status and Nexus hides the ride at the persisted deadline. |
| 10 | Disconnect and reconnect glasses | Nexus restores transport; the plugin sends only the latest persisted ride state. |
| 11 | Open Taxi Plate from the Nexus launcher with an active notification | The only action is `Tap to Clear Notification`; tapping it removes the Android notification and PIN. |
| 12 | Open Taxi Plate without an active notification | The clear action is absent. |
| 13 | Swipe away the Taxi Plate Android notification | The saved ride becomes dismissed and Nexus receives `hidePin`. |
| 14 | Send a test widget from plugin settings | A synthetic Android notification and ride PIN are shown. |
| 15 | Put glasses to sleep, then post an update | Nexus accepts and holds the PIN, then delivers it after glasses reconnect. |
| 16 | Import a valid second-provider JSON bundle | The new adapter is listed independently and begins matching without an APK update. |
| 17 | Import malformed/unsupported JSON | Import is rejected and all previously working adapters remain unchanged. |
| 18 | Export diagnostics | JSONL contains raw notification fields, adapter identity, parser result, and state decisions. |

Record Nexus phone/glasses versions, Android/Rokid software versions, date, and result.
Do not mark hardware checks passed from unit tests alone.
