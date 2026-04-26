package com.lap.mobile.release

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

private const val BUILD_GRADLE_PATH: String = "build.gradle.kts"
private const val MANIFEST_PATH: String = "src/main/AndroidManifest.xml"
private const val PROD_RELEASE_APK: String = "build/outputs/apk/prod/release/app-prod-release.apk"
private const val APK_SIZE_BUDGET_BYTES: Long = 30L * 1024L * 1024L
private const val PROGUARD_RULES_FILENAME: String = "proguard-rules.pro"

/**
 * JVM regression tests for the R8 release-build configuration (T238).
 *
 * The actual size + startup measurement of a real device-installed
 * release build is benchmarking territory (Macrobenchmark) and is
 * marked [PENDING: device-required] in the sprint. What this test
 * locks is the *configuration* — that the release build is wired
 * for R8 / minify / resource-shrink / signing — so a future refactor
 * that accidentally turns minification off (the classic source of
 * silent APK bloat) breaks here.
 *
 * Plus an opportunistic size-cap check: if the prod-release APK
 * has been built (artifact exists at the standard output path),
 * assert it stays under the documented 30 MB budget. The check
 * gracefully skips via [assumeTrue] when no APK exists — i.e., a
 * `testDebugUnitTest` invocation that didn't build a release APK
 * still passes — so the regression test doesn't force a 40-second
 * release build into the fast unit-test loop.
 *
 * What this test does NOT cover (genuinely device-required, hence
 * the [PENDING: device-required] mark in the sprint file):
 *  - Real cold-start time on Pixel-class hardware via
 *    `androidx.benchmark.macro.junit4.MacrobenchmarkRule` +
 *    `StartupMode.COLD`.
 *  - Real warm-start / hot-start latency.
 *  - Real on-device install footprint (APK on disk vs. installed
 *    app footprint after dex2oat).
 *  - Real first-frame draw time on Compose's first launch.
 *
 * What it DOES cover (the release-build config regression surface):
 *  - `build.gradle.kts` release block declares `isMinifyEnabled = true`.
 *  - Release block declares `isShrinkResources = true`.
 *  - Release block wires `proguardFiles(...)` with the project's
 *    `proguard-rules.pro`.
 *  - Release block wires a signing config (real release key when
 *    `keystore.properties` exists, debug-key fallback otherwise so
 *    pre-account local builds stay sign-able).
 *  - The AndroidManifest removes the auto-merged
 *    AndroidX-Startup `InitializationProvider` (required by the
 *    `RemoveWorkManagerInitializer` lint check when the Application
 *    impls `Configuration.Provider`).
 *  - When a built APK exists, its on-disk size stays under the
 *    documented 30 MB budget.
 */
class R8ReleaseBuildSizeTest {
    private val buildGradleSource: String = File(BUILD_GRADLE_PATH).readText()
    private val manifestSource: String = File(MANIFEST_PATH).readText()

    @Test
    fun release_block_enables_r8_code_minification_so_kotlin_metadata_and_unused_classes_are_stripped() {
        assertTrue(
            "release block must declare isMinifyEnabled = true — without this R8 doesn't run and " +
                "the prod-release APK ships every unused class verbatim",
            buildGradleSource.contains(
                Regex("""release\s*\{[^}]*isMinifyEnabled\s*=\s*true""", RegexOption.DOT_MATCHES_ALL),
            ),
        )
    }

    @Test
    fun release_block_enables_resource_shrinking_so_unreferenced_drawables_and_strings_are_stripped() {
        assertTrue(
            "release block must declare isShrinkResources = true — without this every unreferenced " +
                "drawable / layout / string ships in the APK regardless of code-side R8 stripping",
            buildGradleSource.contains(
                Regex("""release\s*\{[^}]*isShrinkResources\s*=\s*true""", RegexOption.DOT_MATCHES_ALL),
            ),
        )
    }

    @Test
    fun release_block_wires_project_proguard_rules_so_keep_directives_for_room_serialization_apply() {
        assertTrue(
            "release block must include $PROGUARD_RULES_FILENAME — Room/KSP-generated DAOs + kotlinx-serialization " +
                "@Serializable classes both need targeted -keep rules to survive R8 obfuscation",
            buildGradleSource.contains(PROGUARD_RULES_FILENAME),
        )
        assertTrue(
            "$PROGUARD_RULES_FILENAME file must exist next to build.gradle.kts",
            File(PROGUARD_RULES_FILENAME).exists(),
        )
    }

    @Test
    fun release_block_wires_a_signing_config_so_the_apk_is_install_able_even_in_local_debug_key_fallback() {
        // The build.gradle uses a conditional that picks the real release
        // key when keystore.properties exists, falling back to debug for
        // local pre-account builds so engineers can still install + smoke-test.
        // The prior assembleProdRelease run that produced this commit's
        // baseline succeeded under the debug-key fallback path.
        assertTrue(
            "release block must wire signingConfig — unsigned APKs aren't installable",
            buildGradleSource.contains(Regex("""release\s*\{[^}]*signingConfig\s*=""", RegexOption.DOT_MATCHES_ALL)),
        )
    }

    @Test
    fun manifest_removes_androidx_startup_initialization_provider_so_workmanager_on_demand_init_works() {
        // Headline T238 lint fix: WorkManager + Hilt's on-demand init
        // (LapApplication impls Configuration.Provider) requires the
        // auto-merged AndroidX-Startup InitializationProvider to be
        // removed via tools:node="remove" — otherwise the lintVitalProdRelease
        // RemoveWorkManagerInitializer check fails fatally and blocks the
        // release build entirely.
        assertTrue(
            "manifest must declare xmlns:tools so tools:node=\"remove\" is recognised",
            manifestSource.contains("xmlns:tools=\"http://schemas.android.com/tools\""),
        )
        assertTrue(
            "manifest must declare androidx.startup.InitializationProvider with tools:node=\"remove\"",
            manifestSource.contains("androidx.startup.InitializationProvider") &&
                manifestSource.contains("tools:node=\"remove\""),
        )
    }

    @Test
    fun built_prod_release_apk_when_present_stays_under_documented_30_mb_size_budget() {
        val apk = File(PROD_RELEASE_APK)
        // Skip rather than fail when the APK doesn't exist — a fast
        // testDebugUnitTest invocation that didn't run the
        // 40-second assembleProdRelease pipeline still passes; only
        // a CI run that actually built the APK exercises the size
        // regression check.
        assumeTrue("prod-release APK not built; run ./gradlew app:assembleProdRelease first", apk.exists())

        val sizeBytes = apk.length()
        val sizeMb = sizeBytes / 1024 / 1024
        val budgetMb = APK_SIZE_BUDGET_BYTES / 1024 / 1024
        assertTrue(
            "prod-release APK is ${sizeMb}MB (${sizeBytes}B), exceeds documented ${budgetMb}MB budget — " +
                "investigate via ./gradlew app:dependenciesProdRelease + R8 mapping file",
            sizeBytes <= APK_SIZE_BUDGET_BYTES,
        )
    }
}
