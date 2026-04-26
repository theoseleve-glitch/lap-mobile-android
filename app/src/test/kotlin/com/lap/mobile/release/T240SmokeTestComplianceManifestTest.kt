package com.lap.mobile.release

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val SPRINT_FILE_PATH: String = "../docs/roadmap/sprint-30day/sprint-mobile.md"
private const val FALLBACK_SPRINT_FILE_PATH: String =
    "../../docs/roadmap/sprint-30day/sprint-mobile.md"

/**
 * JVM compliance manifest for T240 (instrumented sibling: clean-device
 * smoke test — install, sign up, first session, upload ⇒ green).
 *
 * The smoke test itself runs on a real device against a real backend
 * + Play Console + Clerk auth flow — which is `[PENDING:
 * device-required + multiple-other-gates]` for the instrumented half.
 * What this manifest locks at the JVM level is the *assumption* that
 * each smoke-test leg has a JVM-equivalent regression class behind
 * it: if a future refactor renames or removes any of these proxy
 * classes, the smoke test's "JVM coverage exists" claim silently
 * becomes false, and this manifest catches the drift before it
 * matters.
 *
 * Each smoke-test leg maps to a JVM proxy:
 *
 *  - **install** ← [com.lap.mobile.release.R8ReleaseBuildSizeTest]
 *    locks the R8 release-build configuration (T238) — `isMinifyEnabled`,
 *    `isShrinkResources`, `proguardFiles`, signing, manifest fix for
 *    `RemoveWorkManagerInitializer`, ≤30 MB APK size budget. Without
 *    this the install path on a real device might fetch a 100 MB
 *    APK, fail the lintVitalProdRelease gate, or land an unsigned
 *    artifact.
 *
 *  - **sign-up** ← `[BLOCKED via T171]` — Clerk Android SDK 0.1.28
 *    needs the Block-I toolchain bump (Kotlin 2.3 + AGP 8.9.1 +
 *    compileSdk 36). Until then the sign-up step has no working
 *    runtime implementation; the kernel scaffolds for token storage
 *    (T173), token refresh (T174), logout (T175), magic-link (T177),
 *    Apple sign-in (T176), and pairing (T179) ARE all JVM-tested
 *    but the Clerk runtime that connects them is the gap.
 *
 *  - **first session** ← four classes:
 *    [com.lap.mobile.data.SessionCoordinatorTest] (T076 — IDLE→ARMED→
 *    RECORDING→FINALIZING state machine), [com.lap.mobile.capture.CaptureOrchestratorTest]
 *    (T071 — location + motion sample fan-in), [com.lap.mobile.capture.DefaultPreflightServiceTest]
 *    (T086 — preflight gate with all 9 hard-block + 2 soft-warn checks),
 *    [com.lap.mobile.data.CrashSafeBufferTest] (T079 — bounded in-memory
 *    + size + time-trigger flush). Plus the Block-D scenario tests
 *    landed in this loop run (T083 60s sample count, T084 kill-and-
 *    restart recovery, T085 airplane-mode continues-locally).
 *
 *  - **upload** ← four classes:
 *    [com.lap.mobile.upload.CaptureToUploadFlowTest] (T228 — full
 *    BundleMultipartBuilder + IngestApi + UploadOrchestration against
 *    MockWebServer), [com.lap.mobile.upload.IngestApiTest] (T148 —
 *    Retrofit contract + multipart + Idempotency-Key header),
 *    [com.lap.mobile.upload.BackoffScheduleTest] (T154 — bounded curve +
 *    0..25% jitter + cap policy), [com.lap.mobile.upload.IdempotentUploadEnqueuerTest]
 *    (T150 — UUIDv7 idempotency-key reuse on retry).
 *
 * What the smoke test on a real device adds OVER this JVM coverage
 * (genuinely device-required, hence T240's overall PENDING):
 *  - Real Play Store install + first-launch path on a phone that's
 *    never seen the app before (catches Application-onCreate timing
 *    bugs, unfilled BuildConfig fields, missing runtime permissions
 *    on a fresh Android 14+ device).
 *  - Real Clerk sign-up against the production auth backend
 *    (post-Block-I-toolchain-bump).
 *  - Real GNSS + IMU + foreground-service interaction over a real
 *    60+ second recording (T083 simulates this with FixedLocationDataSource;
 *    real hardware proves rate-sustain under thermal/doze pressure).
 *  - Real network-level upload against the production ingest
 *    endpoint (T228 simulates this against MockWebServer; real
 *    networks have TLS handshake quirks, MTU/fragmentation, mid-flight
 *    disconnects).
 */
class T240SmokeTestComplianceManifestTest {
    @Test
    fun install_leg_has_jvm_coverage_via_r8_release_build_size_test() {
        assertJvmProxyClassExists(
            "com.lap.mobile.release.R8ReleaseBuildSizeTest",
            "T238 R8 release-build configuration (isMinifyEnabled / isShrinkResources / " +
                "proguardFiles / signingConfig / manifest InitializationProvider removal / " +
                "≤30 MB APK size budget) — proxies T240's install leg",
        )
    }

    @Test
    fun first_session_leg_has_jvm_coverage_via_session_coordinator_capture_orchestrator_preflight_buffer() {
        assertJvmProxyClassExists(
            "com.lap.mobile.data.SessionCoordinatorTest",
            "T076 IDLE→ARMED→RECORDING→FINALIZING state machine — proxies T240's first-session leg state walk",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.capture.CaptureOrchestratorTest",
            "T071 location + motion sample fan-in — proxies T240's first-session leg sample collection",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.capture.DefaultPreflightServiceTest",
            "T086 preflight gate (9 hard-block + 2 soft-warn checks) — proxies T240's first-session preflight",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.data.CrashSafeBufferTest",
            "T079 bounded in-memory + size + time-trigger flush — proxies T240's first-session local-write path",
        )
    }

    @Test
    fun first_session_leg_also_has_block_d_scenario_coverage_landed_this_loop_run() {
        assertJvmProxyClassExists(
            "com.lap.mobile.data.Recording60sSampleCountScenarioTest",
            "T083 60s recording at production rates yields >3000 samples — proxies T240's session duration",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.data.KillAndRestartJournalRecoveryScenarioTest",
            "T084 kill-and-restart journal recovery — proxies T240's mid-session-recovery resilience",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.data.AirplaneModeRecordingScenarioTest",
            "T085 airplane mode capture continues locally — proxies T240's offline-during-session resilience",
        )
    }

    @Test
    fun upload_leg_has_jvm_coverage_via_capture_to_upload_ingest_backoff_idempotent() {
        assertJvmProxyClassExists(
            "com.lap.mobile.upload.CaptureToUploadFlowTest",
            "T228 full BundleMultipartBuilder + IngestApi + UploadOrchestration end-to-end — proxies T240's upload leg",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.upload.IngestApiTest",
            "T148 Retrofit contract + multipart + Idempotency-Key header — proxies T240's upload-API leg",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.upload.BackoffScheduleTest",
            "T154 bounded curve + 0..25% jitter + cap policy — proxies T240's upload-retry-on-flaky-network leg",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.upload.IdempotentUploadEnqueuerTest",
            "T150 UUIDv7 idempotency-key reuse on retry — proxies T240's upload-idempotency leg",
        )
    }

    @Test
    fun signup_leg_documented_as_blocked_via_t171_clerk_toolchain_gap_in_sprint_file() {
        // The sign-up leg has no JVM proxy class because Clerk SDK is
        // gated on the Block-I toolchain bump (T171). This test locks
        // that the BLOCKED note remains in the sprint file — if the
        // note ever gets removed (e.g., by a stale sprint-file edit),
        // this manifest surfaces the inconsistency before someone
        // reads it as "sign-up has full coverage".
        val sprintFile = locateSprintFile()
        assertNotNull("could not locate sprint-mobile.md from cwd", sprintFile)
        val sprintContent = sprintFile!!.readText()
        assertTrue(
            "sprint-mobile.md must mention T171 + BLOCKED so smoke-test sign-up leg gap is " +
                "documented — current cwd = ${File(".").absolutePath}",
            sprintContent.contains("T171") && sprintContent.contains("BLOCKED"),
        )
    }

    @Test
    fun signup_leg_kernels_for_post_clerk_wire_up_have_jvm_coverage_so_glue_layer_lands_quickly() {
        // While the Clerk runtime is gated, the kernels that Clerk
        // will eventually plug into ARE all JVM-tested. Locks that
        // the post-Block-I integration work is "wire Clerk to these"
        // rather than "build the kernels too".
        assertJvmProxyClassExists(
            "com.lap.mobile.auth.PairingRedeemUseCaseTest",
            "T179 pairing-flow kernel (PairingRedeemer fun-interface seam) — Clerk plugs in here",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.auth.MagicLinkUseCaseTest",
            "T177 magic-link kernel (MagicLinkSender + MagicLinkVerifier seams) — Clerk plugs in here",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.auth.AppleSignInUseCaseTest",
            "T176 Apple-sign-in kernel (AppleAuthProvider + AppleCredentialExchanger seams) — Clerk plugs in here",
        )
    }

    private fun assertJvmProxyClassExists(fqcn: String, contractDescription: String) {
        try {
            Class.forName(fqcn)
        } catch (cnfe: ClassNotFoundException) {
            throw AssertionError(
                "T240 smoke-test JVM proxy missing: $fqcn\n" +
                    "Contract: $contractDescription\n" +
                    "If this class was renamed/removed, T240's BLOCKED-note enumeration of JVM " +
                    "coverage is now stale — update both this manifest and the BLOCKED note.",
                cnfe,
            )
        }
    }

    /**
     * cwd is `app/` when run from `./gradlew app:testDebugUnitTest`,
     * but can be the project root when run from an IDE. Try both.
     */
    private fun locateSprintFile(): File? {
        val primary = File(SPRINT_FILE_PATH)
        if (primary.exists()) return primary
        val fallback = File(FALLBACK_SPRINT_FILE_PATH)
        if (fallback.exists()) return fallback
        return null
    }
}
