# Play Store metadata layout

Layout follows gradle-play-publisher's convention so a future
`gradle-play-publisher` plugin (or `bootstrap`) can pick this up
without restructuring:

```
play/
├── default-language.txt          → en-US
├── contact-email.txt             → support@lap.app
├── app-category.txt              → SPORTS (Play Console category)
└── listings/
    └── en-US/
        ├── title.txt             → ≤30 chars (Play Store hard cap)
        ├── short-description.txt → ≤80 chars
        ├── full-description.txt  → ≤4000 chars
        └── graphics/             → screenshots + feature graphic
            ├── NOTES.md          → blocked-by-design / pending-by-device
            └── phone-screenshots/
                └── README.md
└── release-notes/
    └── en-US/
        └── default.txt           → ≤500 chars (Play Store hard cap)
```

Category choice — SPORTS — picked because:
- The app is a track-day tool for amateur and pro drivers
- AUTO_AND_VEHICLES is more about car ownership / shopping, not active sport
- Google Play's own example for sport-tracking apps (e.g., GPS-based
  cycling/running trackers) all sit under SPORTS

Length caps locked by JVM regression test:
`app/src/test/kotlin/com/lap/mobile/play/PlayStoreMetadataTest.kt`

Image-asset and on-device-screenshot work tracked under the per-asset
NOTES files inside `listings/en-US/graphics/`.
