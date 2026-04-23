# CLAUDE.md — lap-mobile-android

## Context
Android-first mobile capture app. Kotlin 2.0 + Compose + Gradle. Builds on Windows.

## Conventions
- Single-module for v1 (`app/`), extract modules when exceeding ~15K LOC.
- Layout: `app/src/main/kotlin/com/lap/mobile/` with packages: `capture/`, `ingest/`, `coaching/`, `ui/`, `data/`, `di/`.
- Hilt for DI. No manual service locators.
- Room for all local persistence. No raw SQLite.
- Kotlinx Serialization for all JSON. No Gson.
- Kotlin Coroutines + Flow for async. No RxJava.
- Every ViewModel has a StateFlow exposing UiState.
- Every repository returns Result<T, DomainError>.
- Compose screens via single composable per file, @Preview included.

## Forbidden
- No local coaching logic beyond rule-engine execution (rules authored server-side).
- No local lap/sector detection (server computes from raw GPS).
- No duplicate Pydantic-equivalent types — import generated Kotlin from @lap/schemas.
- No blocking calls on main thread.
- No hardcoded strings — use strings.xml.
- No `--no-verify` on commits.

## /loop rules
- Read sprint file, pick next `[ ]`, complete fully.
- Build verification: `./gradlew app:assembleDebug` must succeed for any code-changing task.
- Tests: `./gradlew app:testDebugUnitTest` must be green.
- Commit: `sprint(mobile): T### <short>`.
- Invoke superpowers:test-driven-development for new features.
- Invoke superpowers:verification-before-completion before claiming done.
