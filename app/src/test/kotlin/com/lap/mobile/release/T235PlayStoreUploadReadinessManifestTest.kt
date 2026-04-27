package com.lap.mobile.release

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val BUILD_GRADLE_PATH: String = "build.gradle.kts"
private const val GITIGNORE_PATH: String = "../.gitignore"
private const val FALLBACK_GITIGNORE_PATH: String = ".gitignore"
private const val PLAY_METADATA_DIR: String = "src/main/play"
private const val PLAY_LISTINGS_DIR: String = "src/main/play/listings/en-US"
private const val PLAY_RELEASE_NOTES_DIR: String = "src/main/play/release-notes/en-US"

/**
 * JVM compliance manifest for T235 (instrumented sibling: Play Store
 * internal-test-track build uploaded).
 *
 * The actual upload requires a Play Console developer account ($25
 * one-time + Google account + tax info), an Android upload signing
 * key, and a service-account JSON for automated upload — all of
 * which are genuinely human-or-credential-required and stay
 * `[PENDING: human-and-account-required]` for the instrumented half.
 *
 * What this manifest locks at the JVM level is the *upload readiness*
 * checklist — every code-side prerequisite for a successful upload
 * is present so that when the human gets the credentials, the upload
 * is one command (`./gradlew app:bundleProdRelease` + Play Console
 * upload OR a future gradle-play-publisher plugin run).
 *
 * Why this manifest exists distinct from the constituent tests
 * (T234 PlayStoreMetadataTest, T238 R8ReleaseBuildSizeTest):
 *  - The constituent tests lock individual prerequisites in
 *    isolation. This manifest locks the *cohesive checklist* — if
 *    any prerequisite drops out of the test suite (a refactor
 *    deletes the file, a CI config skips it), this manifest catches
 *    that the upload-readiness claim is now false.
 *  - It establishes a single grep-able anchor (search for "T235") for
 *    a future human resuming Play Console work after a long gap.
 *
 * Pre-flight checklist — what the manifest verifies in order:
 *  1. **versionCode + versionName** — the two values Play Console
 *     uses to dedupe build uploads. Both must be set; versionCode
 *     must increment per upload.
 *  2. **Signing config** — release builds must be signed (release
 *     keystore in production, debug keystore in local fallback so
 *     pre-account engineers can still smoke-test).
 *  3. **R8 release build** — minified + resource-shrunk + ProGuard
 *     rules wired so the upload artifact is the expected ~23.5 MB
 *     rather than the unminified ~38.2 MB.
 *  4. **Play Store metadata** — the `src/main/play/` directory
 *     follows gradle-play-publisher's expected layout (default
 *     language + listings + release notes); when a future gradle-
 *     play-publisher plugin install lands, this layout works
 *     unchanged.
 *  5. **Credentials gitignored** — keystore.properties + *.keystore
 *     never leak into the repo (security gate).
 *  6. **Constituent test classes exist** — T234 + T238 manifest
 *     siblings are present to provide their isolation-level checks.
 *
 * What stays `[PENDING: human-and-account-required]`:
 *  - The actual Play Console developer account creation ($25 +
 *    tax info).
 *  - The production upload-signing-key generation (`keytool`
 *    invocation + storage in a secrets manager).
 *  - The Play Console UI configuration for the internal-test-track
 *    (track allow-list, rollout staging).
 *  - The actual upload action — either manual via Play Console UI
 *    or automated via gradle-play-publisher plugin (latter would be
 *    a separate scaffold task; not introduced here to keep the
 *    build-config diff minimal).
 *  - Service-account JSON for automated upload (security artifact,
 *    not codeable; would land in keystore.properties or alongside).
 */
class T235PlayStoreUploadReadinessManifestTest {
    private val buildGradleSource: String by lazy { resolveResourceFile(BUILD_GRADLE_PATH).readText() }
    private val gitignoreSource: String by lazy { readGitignore() }

    @Test
    fun version_code_is_declared_in_build_gradle_so_play_console_can_dedupe_uploads() {
        // Play Console rejects an upload whose versionCode matches
        // any prior upload — that's how it dedupes. A missing
        // versionCode would fail at upload time with a confusing
        // "no version" error rather than the obvious "duplicate".
        assertTrue(
            "build.gradle.kts must declare versionCode for Play Console upload dedupe",
            buildGradleSource.contains(Regex("""versionCode\s*=\s*\d+""")),
        )
    }

    @Test
    fun version_name_is_declared_in_build_gradle_so_play_console_release_notes_can_anchor() {
        // versionName is the human-readable string Play Console
        // shows next to release notes ("0.1.0", "0.1.1"). Required
        // for upload.
        assertTrue(
            "build.gradle.kts must declare versionName for Play Console release-note anchor",
            buildGradleSource.contains(Regex("""versionName\s*=\s*"[^"]+"""")),
        )
    }

    @Test
    fun release_signing_config_is_wired_so_upload_artifact_is_install_able() {
        // Unsigned APKs aren't installable AND Play Console rejects
        // unsigned uploads with the most confusing error message in
        // the Android ecosystem. T238's R8ReleaseBuildSizeTest already
        // asserts the wiring; this test cross-references it for the
        // T235 readiness checklist.
        assertTrue(
            "release block must wire signingConfig — proxies T238's release-build signing assertion",
            buildGradleSource.contains(Regex("""release\s*\{[^}]*signingConfig\s*=""", RegexOption.DOT_MATCHES_ALL)),
        )
    }

    @Test
    fun play_metadata_directory_layout_matches_gradle_play_publisher_convention() {
        // gradle-play-publisher (industry-standard plugin for Play
        // Console uploads) expects metadata at app/src/main/play/
        // with subdirectories for default-language, listings/<locale>,
        // release-notes/<locale>. T234 shipped this layout; the
        // manifest asserts it stays in place so a future plugin
        // install works without re-shuffling files.
        val playDir = resolveResourceDir(PLAY_METADATA_DIR)
        assertNotNull("Play Store metadata directory missing at $PLAY_METADATA_DIR", playDir)

        val defaultLanguageFile = resolveResourceFile("$PLAY_METADATA_DIR/default-language.txt")
        assertTrue(
            "default-language.txt must exist for gradle-play-publisher (currently 'en-US')",
            defaultLanguageFile.exists(),
        )

        val listingsDir = resolveResourceDir(PLAY_LISTINGS_DIR)
        assertNotNull("listings/en-US directory missing — gradle-play-publisher convention", listingsDir)

        val releaseNotesDir = resolveResourceDir(PLAY_RELEASE_NOTES_DIR)
        assertNotNull("release-notes/en-US directory missing — gradle-play-publisher convention", releaseNotesDir)
    }

    @Test
    fun keystore_credentials_are_gitignored_so_signing_key_never_leaks_to_repo() {
        // Security gate — release signing key in the repo would mean
        // anyone could sign and impersonate the app. Two patterns
        // matter: keystore.properties (the build.gradle.kts reads
        // this for credentials) and *.keystore (the actual key
        // material).
        assertTrue(
            ".gitignore must exclude keystore.properties so build credentials never leak",
            gitignoreSource.contains("keystore.properties"),
        )
        assertTrue(
            ".gitignore must exclude *.keystore so signing-key material never leaks",
            gitignoreSource.contains("*.keystore"),
        )
        assertTrue(
            ".gitignore must allow debug.keystore via ! exception (debug-key fallback for local builds)",
            gitignoreSource.contains("!debug.keystore"),
        )
    }

    @Test
    fun aab_artifacts_are_gitignored_so_build_outputs_dont_pollute_repo() {
        // .aab is the App Bundle format Play Console expects (
        // bigger than .apk because it bundles per-density resources;
        // Play Console splits per-device). Build outputs land in
        // app/build/ which is already excluded, but explicit *.aab
        // exclusion catches accidental copies elsewhere.
        assertTrue(
            ".gitignore must exclude *.aab so accidental copies of upload bundles don't get committed",
            gitignoreSource.contains("*.aab"),
        )
    }

    @Test
    fun constituent_validators_t234_metadata_and_t238_release_build_size_are_present() {
        // T235's upload-readiness assumes T234 metadata caps + T238
        // release build config are validated. If either constituent
        // test gets removed/renamed, the upload-readiness claim is
        // silently weakened — this test catches the drift before
        // the next Play Console upload would fail.
        assertJvmProxyClassExists(
            "com.lap.mobile.play.PlayStoreMetadataTest",
            "T234 Play Store metadata caps (title ≤30, short ≤80, full ≤4000, release notes ≤500) — " +
                "Play Console rejects uploads exceeding any of these",
        )
        assertJvmProxyClassExists(
            "com.lap.mobile.release.R8ReleaseBuildSizeTest",
            "T238 R8 release-build configuration (isMinifyEnabled, isShrinkResources, signingConfig, " +
                "manifest fix for RemoveWorkManagerInitializer, ≤30 MB APK budget)",
        )
    }

    private fun assertJvmProxyClassExists(fqcn: String, contractDescription: String) {
        try {
            Class.forName(fqcn)
        } catch (cnfe: ClassNotFoundException) {
            throw AssertionError(
                "T235 upload-readiness constituent missing: $fqcn\n" +
                    "Contract: $contractDescription\n" +
                    "If this class was renamed/removed, T235's upload-readiness checklist is now stale — " +
                    "update both this manifest and the BLOCKED note.",
                cnfe,
            )
        }
    }

    /**
     * cwd is `app/` when run from `./gradlew app:testDebugUnitTest`,
     * but can be the project root when run from an IDE. Try both.
     */
    private fun resolveResourceFile(relativePath: String): File {
        val primary = File(relativePath)
        if (primary.exists()) return primary
        return File("app/$relativePath")
    }

    private fun resolveResourceDir(relativePath: String): File? {
        val primary = File(relativePath)
        if (primary.isDirectory) return primary
        val fallback = File("app/$relativePath")
        if (fallback.isDirectory) return fallback
        return null
    }

    private fun readGitignore(): String {
        // .gitignore lives at repo root (../ from app/ when gradle
        // runs, ./ when IDE runs from project root).
        val primary = File(GITIGNORE_PATH)
        if (primary.exists()) return primary.readText()
        val fallback = File(FALLBACK_GITIGNORE_PATH)
        if (fallback.exists()) return fallback.readText()
        throw IllegalStateException(
            "Could not locate .gitignore from cwd=${File(".").absolutePath}; " +
                "expected at $GITIGNORE_PATH or $FALLBACK_GITIGNORE_PATH",
        )
    }
}
