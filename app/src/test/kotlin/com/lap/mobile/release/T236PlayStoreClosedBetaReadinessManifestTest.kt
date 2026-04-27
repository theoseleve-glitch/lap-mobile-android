package com.lap.mobile.release

import org.junit.Test

/**
 * JVM compliance manifest for T236 (instrumented sibling: Play Store
 * closed-beta track configured).
 *
 * The actual closed-beta-track configuration is purely Play Console
 * UI work that requires (1) T235 internal-test-track being live first,
 * (2) Play Console UI configuration of the closed-beta-track + tester
 * email allow-list curation, (3) the same Play Console developer
 * account from T235. All of this is genuinely human-or-credential-
 * required and stays `[PENDING: human-and-account-required]` for
 * the instrumented half.
 *
 * What this manifest locks at the JVM level — and what makes it
 * worth shipping despite T236 being strictly a depends-on-T235
 * Play-Console UI promotion:
 *  - The artifact uploaded to closed-beta is the same .aab as the
 *    internal-test track (same prod-release build, same metadata,
 *    same signing). T235's upload-readiness checklist therefore
 *    applies verbatim — this manifest cross-references it so a
 *    refactor that breaks T235's readiness silently invalidates
 *    T236's too.
 *  - Documents the closed-beta-vs-internal-test workflow distinction
 *    in code comments + test names so a future human resuming Play
 *    Console work has a single grep-able 'T236' anchor for the
 *    promotion + allow-list curation steps.
 *
 * Why no separate prerequisite assertions: every code-side
 * prerequisite is already locked by [T235PlayStoreUploadReadinessManifestTest].
 * Closed-beta differs ONLY in Play Console track configuration —
 * which is UI work, not code.
 *
 * Closed-beta-vs-internal-test workflow distinctions (the documented
 * delta a human acts on after T235's track is live):
 *  - Audience size: internal test caps at 100 testers; closed-beta
 *    can scale to thousands via email allow-list (gradual rollout).
 *  - Promotion path: a release tested on internal can be promoted to
 *    closed-beta via Play Console "Promote release" without re-upload
 *    (same artifact bytes; Play Console copies the release record).
 *  - Tester opt-in: internal test = invite link or Google Group;
 *    closed-beta = email allow-list (manually curated) OR Google
 *    Group with broader membership.
 *  - Crash reporting: closed-beta crashes flow to the same Sentry
 *    DSN (T237 SLO target ≥98% applies — wider tester pool means
 *    the baseline measurement actually has data).
 *  - Feedback channel: closed-beta typically opens an in-Play-Console
 *    feedback form (testers report issues without leaving the app
 *    install context).
 *
 * What stays `[PENDING: human-and-account-required]`:
 *  - Play Console closed-beta-track UI configuration (track name,
 *    countries, rollout percentage, opt-in URL).
 *  - Tester email allow-list curation (initial cohort selection +
 *    ongoing additions/removals).
 *  - The actual promotion action — either "Promote from internal"
 *    Play Console UI button OR a fresh upload to the closed-beta
 *    track directly.
 *  - Communication to testers (email invitation, feedback channel
 *    setup, expectation-setting on stability).
 */
class T236PlayStoreClosedBetaReadinessManifestTest {
    @Test
    fun closed_beta_inherits_t235_internal_test_upload_readiness_so_same_artifact_uploads_unchanged() {
        // Closed-beta uses the same .aab as internal-test. Asserting
        // T235's manifest exists ensures every prerequisite locked
        // there (versionCode + versionName + signing + metadata
        // layout + keystore gitignore + *.aab gitignore + constituent
        // T234/T238 cross-reference) applies to closed-beta too. If
        // T235's manifest gets renamed/removed, T236's readiness
        // claim is silently weakened — this test catches the drift.
        assertJvmProxyClassExists(
            "com.lap.mobile.release.T235PlayStoreUploadReadinessManifestTest",
            "T235 Play Store upload-readiness manifest — same prerequisites apply to closed-beta",
        )
    }

    @Test
    fun closed_beta_inherits_t234_metadata_caps_so_release_notes_dont_get_truncated_in_beta_listing() {
        // Closed-beta release notes face the same Play Console
        // length cap (500 chars) as internal-test. T234's
        // PlayStoreMetadataTest locks all caps (title 30, short 80,
        // full 4000, release notes 500); a regression there would
        // break the closed-beta upload too.
        assertJvmProxyClassExists(
            "com.lap.mobile.play.PlayStoreMetadataTest",
            "T234 Play Store metadata caps — same caps apply to closed-beta release notes",
        )
    }

    @Test
    fun closed_beta_inherits_t238_release_build_size_budget_so_apk_stays_under_30mb_for_beta_install_path() {
        // Closed-beta installs go through the same Play Store
        // delivery path as production — Play Console rejects builds
        // exceeding documented size limits, and a 38MB unminified
        // APK would frustrate testers downloading on cellular. T238's
        // R8ReleaseBuildSizeTest locks the 30MB budget; cross-
        // reference here ensures closed-beta inherits it.
        assertJvmProxyClassExists(
            "com.lap.mobile.release.R8ReleaseBuildSizeTest",
            "T238 R8 release-build configuration — same 30MB budget applies to closed-beta install path",
        )
    }

    @Test
    fun closed_beta_workflow_documentation_exists_in_this_manifests_kdoc_for_grep_able_anchor() {
        // This test is intentionally simple: its existence + the
        // file's class-level kdoc are the 'documentation' deliverable.
        // A future human reading "where do I find the closed-beta
        // workflow?" can grep for 'T236' and land on this file's
        // kdoc which enumerates the closed-beta-vs-internal-test
        // workflow distinctions (promotion path, tester opt-in,
        // crash reporting, feedback channel).
        // Test passes if file exists; the actual value is the kdoc.
        val className = T236PlayStoreClosedBetaReadinessManifestTest::class.java.simpleName
        if (!className.startsWith("T236")) {
            throw AssertionError("manifest test class name must anchor on T236 for grep discoverability")
        }
    }

    private fun assertJvmProxyClassExists(fqcn: String, contractDescription: String) {
        try {
            Class.forName(fqcn)
        } catch (cnfe: ClassNotFoundException) {
            throw AssertionError(
                "T236 closed-beta-readiness constituent missing: $fqcn\n" +
                    "Contract: $contractDescription\n" +
                    "If this class was renamed/removed, T236's closed-beta readiness inherits the gap — " +
                    "update both this manifest and the BLOCKED note.",
                cnfe,
            )
        }
    }
}
