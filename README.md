# lap-mobile-android

Android-first mobile app for LAP. Builds natively on Windows via Android Studio / Gradle.

## Why Android first

No Mac available. Swift iOS work (frozen in `../lap_mobile/`) resumes when Mac arrives. Android covers the mobile persona on iOS-less timelines and was already in scope per roadmap M5.

## Sprint queue

`C:/Users/Admin/Documents/Telemetry/docs/roadmap/sprint-30day/sprint-mobile.md`

## Architecture

Same doctrine as iOS version — ADRs still apply:
- **ADR-006** — Bundle v2 writer
- **ADR-007** — Dual-timestamp samples (motion, location, RaceBox GNSS)
- **ADR-011** — Clerk for auth (Android SDK)
- **ADR-012** — Unified ingestion endpoint
- **ADR-013** — Client-side rules engine execution

## Status

Seeded 2026-04-23. Scaffold comes from sprint task T001 (Gradle project + Compose setup).

## Stack

- Kotlin 2.0
- Jetpack Compose (UI)
- Hilt (DI)
- Room (SQLite — journal, upload queue, session cache)
- WorkManager (background uploads)
- Retrofit + OkHttp (HTTP client)
- Kotlinx Serialization (JSON/binary)
- Kotlin BLE (RaceBox)
- FusedLocationProviderClient (Google Play Services)
- SensorManager (IMU)
- Clerk Android SDK
- Sentry + PostHog Android SDKs

## Later: Wear OS companion

In scope per questionnaire Q120. Sub-project `wear/` to be created in M5/W19 range.
