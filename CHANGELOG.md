# Changelog

All notable changes to the LAP Android app are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Notes
- M1 closeout in progress. Entries land here as remaining T239+ tasks ship,
  then promote to a `[0.2.0]` section when M2 cuts.

## [0.1.0] — 2026-04-26

First milestone (M1) — pre-release foundation. The app boots,
authenticates (placeholder), arms a session, gates start on a real
preflight readiness check, captures GPS + IMU samples to a crash-safe
local buffer, finalises into a v2 bundle, and queues the bundle for
upload via WorkManager with idempotent server delivery.

### Added

#### Project foundation (Block A — T001–T025)
- Kotlin 2.0 + Jetpack Compose + Gradle 8.7.3 module scaffold (`app/`).
- KSP + Hilt + Room wiring with single-module DI (`CoreModule`).
- Detekt + ktlint + pre-commit hook contract (`assembleDebug` +
  `ktlintFormat` gated on every commit).
- 3-flavor BuildConfig (`dev` / `staging` / `prod`) with environment-
  scoped `API_BASE_URL`, `CLERK_PUBLISHABLE_KEY`, `SENTRY_DSN`,
  `POSTHOG_KEY` placeholders.
- Sealed `Routes` navigation surface and Compose `MainActivity`.
- Dispatcher provider, kotlinx-serialization Json with polymorphic
  discriminator, `Result<T, DomainError>` wrapper.

#### Schema integration (Block B — T026–T040)
- Generated Kotlin schemas imported from `lap-schemas` module (no
  hand-typed Pydantic-equivalent — single source of truth).
- `TelemetrySample` + `LapRecord` + `SessionManifest` + `DataQuality`
  + `EnvironmentTag` + `SensorConfigSnapshot` types wired through.
- ADR-007 dual-timestamp contract enforced
  (`tSourceNs` + `tCanonicalNs` + `tSourceClock` per sample).

#### Database + journal (Block C — T041–T060)
- Room database with `Session` + `UploadQueue` + `Journal` tables.
- Recovery journal — every state change durably written so a process
  kill mid-session never loses more than the in-memory tail.
- Crashed-session reconciler + finalize-from-journal recovery path.
- Orphan-temp-file reconciler.

#### Location + motion capture (Block D — T061–T085)
- Foreground service (`CaptureForegroundService`) with
  `foregroundServiceType=location` so capture survives backgrounding.
- Fused location data source + motion (IMU) capture service.
- Crash-safe NDJSON sink with size + time flush triggers; in-memory
  buffer bounded at 500 samples regardless of session length.
- `SessionCoordinator` IDLE → ARMED → RECORDING → FINALIZING →
  COMPLETED state machine.

#### Preflight readiness gate (Block E — T086–T100)
- 9 hard-block checks (foreground + background location, notifications,
  bluetooth permission + adapter, track selected, GPS fix quality,
  RaceBox connection, storage availability) + 2 soft-warn checks
  (battery low, thermal high).
- `PreflightSeverity.canStart()` UI policy that disables Start on any
  hard block; `SessionCoordinator` gate is coarser and blocks on any
  NotReady so soft warns must be filtered at the UI layer.
- `PreflightDeepLink` maps each failure reason to the system-settings
  screen the user can fix it from.
- Compose preflight checklist + Start-recording button.

#### RaceBox BLE (Block F — T101–T130)
- Nordic BLE manager + UBX parser pipeline (NAV-PVT, NAV-PVAT,
  NAV-RELPOSNED, NAV-STATUS, MON-VER, ESF-MEAS, ESF-RAW, ESF-STATUS,
  ESF-ALG, SEC-UNIQID).
- Scan + connect + auto-reconnect controllers.
- BLE-permission decision flow (grant → rationale → settings).
- Mid-session disconnect recovery: bounded 3-attempt reconnect, then
  fallback to phone GPS with a journal gap marker.
- `MockRaceboxPeripheral` for instrumented integration testing.

#### Bundle v2 writer (Block G — T131–T145)
- ADR-012-flavoured bundle structure: `manifest.json`,
  `telemetry.csv`, `laps.json`, `events.json`, `checksums.txt`.
- Streaming writer (samples never materialise in heap) +
  retry-on-write-failure + finalize step.
- Bundle size + data-quality metadata logged to journal + UI.

#### Upload queue (Block H — T146–T170)
- WorkManager + Hilt on-demand init (`HiltWorkerFactory`).
- `UploadOrchestration` kernel: dequeue → upload → state transition.
- Retrofit + OkHttp `IngestApi` with multipart manifest + chunks +
  `Idempotency-Key` header (session UUIDv7 reused so retries replay
  the exact key the server first saw).
- Auth interceptor with 401-triggered token refresh + replay.
- Resume cursor (`chunks_sent`) so a partial upload doesn't restart
  from the first chunk.
- Bounded backoff curve with 0..25% jitter.
- Manual-retry CTA + abandon-upload use case.
- Connectivity-kick + airplane-mode-resume + auth-restore-kick policies.

#### Dashboard UI (Block J — T191–T210)
- Compose dashboard composing recording strip + session-list + bundle
  badges.
- Cross-policy integration locking dashboard fields against
  `BackInterceptPolicy` + `ScreenWakePolicy` + `OrientationLockPolicy`.
- ViewModel scaffolds (`DashboardViewModel`, `SessionListViewModel`)
  with `StateFlow<UiState>` exposure.

#### Rules engine + coaching (Block K — T211–T220)
- Server-authored rule bundle fetcher + Room TTL cache + signature
  pinning (no local rule authoring — every rule ships from backend).
- Rules-engine-lite Kotlin evaluator + session coaching hook.
- Compose `CoachingPromptCard`, audio cue, feedback flag, prompt
  throttle (max 1 per 30s).
- `UserSkillLevel` toggle + tier-aware rule subset.

#### Observability + release prep (Block K — T222–T238)
- Sentry SDK init with breadcrumb forwarding + DSN placeholder check.
- PostHog SDK with 4 lifecycle analytics events
  (`app_started`, `session_started`, `session_finalized`,
  `upload_completed`).
- Telemetry opt-out toggle in Settings — both SDKs gated per-call by
  the user preference; toggle takes effect immediately mid-session.
- Repository scaffolds (`SessionRepository`, `UploadRepository`)
  returning `Result<T, DomainError>`.
- JVM integration tests for: full capture-to-upload flow against
  MockWebServer, preflight hard-block scenarios, permission denial
  handling, BLE mid-session disconnect, 30-min recording memory + CPU
  bound.
- Play Store metadata draft (en-US listing + release notes + category)
  with hard-cap regression test (title ≤30, short ≤80, full ≤4000).
- R8 release-build configuration regression test:
  `prod-release` APK ships at **23.5 MB** (38% R8 reduction from the
  unminified prod-debug 38.2 MB), well under the 30 MB documented
  budget.
- Manifest fix for `RemoveWorkManagerInitializer` lint check —
  WorkManager's on-demand init via `Configuration.Provider` requires
  removing the auto-merged AndroidX-Startup `InitializationProvider`.

### Pending — device-required (instrumented or hardware)
The following surfaces are JVM-locked but need a real Android device
or Pixel-class benchmark hardware to verify in production:
- Real Nordic BleManager `onDeviceDisconnected` callback firing.
- Real GATT reconnect against `MockRaceboxPeripheral` or hardware RaceBox.
- Real foreground-service-survives-backgrounding (T126 contract).
- Real `Process.totalRss` / `Debug.MemoryInfo` measurement against the
  ~80 MB foreground budget.
- Real cold-start latency via `androidx.benchmark.macro` Macrobenchmark.
- Phone screenshots for the Play Store listing.

### Blocked — gated on external prerequisites
- **T171–T190** — Clerk SDK 0.1.28 / Kotlin 2.3 / AGP 8.9.1 / compileSdk
  36 toolchain bump required for the auth + QR-pairing surface.
- **T233** — Final brand app icon needs designer + Figma artwork +
  stakeholder approval; current placeholder is a stylized "L" on the
  brand-blue background.
- **T235 / T236** — Play Store internal-test + closed-beta tracks need
  a Play Console developer account + production upload signing key.
- **T237** — Crash-free-users baseline needs real users producing
  crash events through the Sentry SDK in production.
- **T240** — Clean-device smoke test needs a real Android device + sign-up
  flow + end-to-end install/upload path.

### Architecture decisions
Major choices documented in `docs/roadmap/02-ARCHITECTURE-DECISIONS.md`:
- ADR-007 — dual timestamps per sample (`tSourceNs` + `tCanonicalNs`
  + `tSourceClock`) so server-side reasoning can distinguish device-clock
  drift from genuine signal-source changes.
- ADR-012 — server-side ingestion contract: idempotency by session
  UUIDv7, multipart manifest + chunks, server computes lap detection
  + sector splits from raw GPS so the algorithm stays consistent
  across drivers and devices.

[Unreleased]: https://github.com/lap/mobile-android/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/lap/mobile-android/releases/tag/v0.1.0
