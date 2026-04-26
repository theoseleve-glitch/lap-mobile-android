# Phone screenshots — placeholder

Drop 2–8 phone screenshots here as `01_screen.png`, `02_screen.png`, …
gradle-play-publisher and Play Store both require:

- Format: PNG or JPEG
- 16:9 or 9:16 aspect
- Min edge ≥ 320 px, max edge ≤ 3840 px
- File size ≤ 8 MB each

Capture targets (ordered by Play Store best practice — most-compelling first):
1. Active recording screen (in-session GPS overlay + sample counter + BLE badge)
2. Session-list screen (recent sessions with duration + sample counts)
3. Coaching prompt example mid-session
4. Settings → Telemetry opt-out toggle
5. Recovery-from-crash dialog (after a kill+restart)

[PENDING: device-required] — these need a real device + running app to
capture; emulator screenshots without a real GNSS fix would show empty
maps and zero samples and would be rejected by Play Store review for
"misleading" content. Capture once a device is available; the JVM
metadata-length-cap test in
`app/src/test/kotlin/com/lap/mobile/play/PlayStoreMetadataTest.kt`
locks the textual side until then.
