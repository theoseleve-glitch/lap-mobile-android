package com.lap.mobile.release

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val CHANGELOG_PATH: String = "../CHANGELOG.md"
private const val EXPECTED_M1_VERSION_HEADER: String = "## [0.1.0]"
private const val EXPECTED_UNRELEASED_HEADER: String = "## [Unreleased]"
private const val KEEP_A_CHANGELOG_REFERENCE: String = "Keep a Changelog"
private const val SEMVER_REFERENCE: String = "Semantic Versioning"
private const val ADR_007_REFERENCE: String = "ADR-007"
private const val ADR_012_REFERENCE: String = "ADR-012"
private const val MIN_M1_BLOCK_REFERENCES: Int = 8

/**
 * JVM regression test for `CHANGELOG.md` (T239).
 *
 * Locks the structural shape of the changelog — Keep-a-Changelog
 * format + present-and-non-empty Unreleased and 0.1.0 sections +
 * the ADR cross-references that anchor the M1 release notes.
 *
 * Why structure-only and not content snapshot: the changelog text
 * is meant to be edited as the project evolves (every M2+ entry
 * lands in `[Unreleased]` then promotes to a versioned section on
 * cut). Snapshot-testing the wording would force a test edit on
 * every legitimate copy change, which trains the team to ignore
 * the test rather than improve the changelog. Locking only the
 * structural anchors catches the regressions that actually matter:
 * file deleted, version section dropped, ADR cross-references
 * removed (which would orphan the references in the source comments).
 *
 * Path is `../CHANGELOG.md` because JVM tests run with cwd =
 * `app/` (the gradle module dir), and `CHANGELOG.md` lives at the
 * project root next to `README.md` — Android + GitHub UI
 * convention for canonical project artifacts.
 *
 * What this test does NOT cover (intentionally outside the
 * structural-regression scope):
 *  - Wording / phrasing of individual entries (those evolve).
 *  - Whether every commit since the last version is reflected
 *    (out-of-scope for unit tests; release-cut tooling enforces).
 *  - GitHub release-notes formatting at publish time.
 *
 * What it DOES cover (the structural-regression surface):
 *  - File exists at the project root.
 *  - File declares Keep-a-Changelog + SemVer adherence in the preamble.
 *  - Both `[Unreleased]` and `[0.1.0]` sections are present.
 *  - The 0.1.0 section is non-empty (would otherwise ship with a
 *    bare version header and no actual notes).
 *  - The major M1 blocks (Block A through Block K) are referenced
 *    so the structure conveys what landed.
 *  - ADR-007 + ADR-012 cross-references are present — these anchor
 *    the architectural rationale for dual timestamps + ingestion
 *    contract that source comments throughout the codebase point to.
 */
class ChangelogStructureTest {
    private val changelog: String by lazy { File(CHANGELOG_PATH).readText() }

    @Test
    fun changelog_file_exists_at_project_root_so_tooling_and_github_ui_can_discover_it() {
        val file = File(CHANGELOG_PATH)
        assertTrue("missing CHANGELOG.md at $file (resolved: ${file.absolutePath})", file.exists())
        assertTrue("CHANGELOG.md must not be empty", file.length() > 0)
    }

    @Test
    fun changelog_preamble_references_keep_a_changelog_format_so_the_structure_contract_is_documented() {
        assertTrue(
            "CHANGELOG.md must reference '$KEEP_A_CHANGELOG_REFERENCE' so future contributors know the format contract",
            changelog.contains(KEEP_A_CHANGELOG_REFERENCE),
        )
    }

    @Test
    fun changelog_preamble_references_semver_so_version_bumps_follow_a_documented_contract() {
        assertTrue(
            "CHANGELOG.md must reference '$SEMVER_REFERENCE' so version bumps stay disciplined",
            changelog.contains(SEMVER_REFERENCE),
        )
    }

    @Test
    fun changelog_has_unreleased_section_so_in_flight_work_has_a_landing_zone_before_next_cut() {
        assertTrue(
            "CHANGELOG.md must declare '$EXPECTED_UNRELEASED_HEADER' section",
            changelog.contains(EXPECTED_UNRELEASED_HEADER),
        )
    }

    @Test
    fun changelog_has_m1_version_section_per_build_gradle_versionName_so_release_artefacts_align() {
        // build.gradle.kts declares versionName = "0.1.0" — the changelog
        // must carry the matching section header so a release publisher
        // can cross-reference the version against its release notes.
        assertTrue(
            "CHANGELOG.md must declare '$EXPECTED_M1_VERSION_HEADER' section to match build.gradle.kts versionName",
            changelog.contains(EXPECTED_M1_VERSION_HEADER),
        )
    }

    @Test
    fun changelog_m1_section_is_non_empty_so_it_does_not_ship_a_bare_version_header_with_no_notes() {
        // Find the 0.1.0 section and confirm there's body content
        // before the next `## [` (which would be a sibling version
        // section like the [Unreleased] above it or absent in the
        // current file). At minimum the M1 section must mention
        // "Added" — the canonical Keep-a-Changelog category for new
        // capabilities, which an M1 cut is by definition full of.
        val m1Index = changelog.indexOf(EXPECTED_M1_VERSION_HEADER)
        assertTrue("$EXPECTED_M1_VERSION_HEADER section header missing", m1Index >= 0)
        val tail = changelog.substring(m1Index)
        assertTrue(
            "M1 section must contain '### Added' subsection — Keep-a-Changelog convention for new capabilities",
            tail.contains("### Added"),
        )
    }

    @Test
    fun changelog_references_the_major_m1_blocks_so_what_landed_is_legible_at_a_glance() {
        // Loose floor — the M1 section must reference at least 8 of
        // the 11 named Blocks (A through K) so a reader gets a sense
        // of scope. Hard-asserting all 11 would be too brittle to
        // copy edits; 8 is enough to catch a regression that drops
        // an entire Block's notes.
        val blockReferences = listOf(
            "Block A",
            "Block B",
            "Block C",
            "Block D",
            "Block E",
            "Block F",
            "Block G",
            "Block H",
            "Block I",
            "Block J",
            "Block K",
        ).count { changelog.contains(it) }
        assertTrue(
            "CHANGELOG.md references $blockReferences Blocks; expected ≥ $MIN_M1_BLOCK_REFERENCES " +
                "to convey scope of M1 work",
            blockReferences >= MIN_M1_BLOCK_REFERENCES,
        )
    }

    @Test
    fun changelog_anchors_adr_007_dual_timestamps_so_source_comments_pointing_at_it_stay_navigable() {
        // ADR-007 + ADR-012 are referenced in dozens of source-code
        // comments across the capture + upload pipelines. Dropping
        // their CHANGELOG anchor would orphan those references for
        // anyone reading the source-side comments and looking for
        // the architectural rationale.
        assertTrue(
            "CHANGELOG.md must reference $ADR_007_REFERENCE — anchors source-code references to dual-timestamp ADR",
            changelog.contains(ADR_007_REFERENCE),
        )
    }

    @Test
    fun changelog_anchors_adr_012_ingestion_contract_so_source_comments_pointing_at_it_stay_navigable() {
        assertTrue(
            "CHANGELOG.md must reference $ADR_012_REFERENCE — anchors source-code references to ingestion ADR",
            changelog.contains(ADR_012_REFERENCE),
        )
    }
}
