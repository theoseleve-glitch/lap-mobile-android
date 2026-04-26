package com.lap.mobile.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val PLAY_DIR: String = "src/main/play"
private const val LISTING_LANGUAGE: String = "en-US"
private const val TITLE_MAX_CHARS: Int = 30
private const val SHORT_DESCRIPTION_MAX_CHARS: Int = 80
private const val FULL_DESCRIPTION_MAX_CHARS: Int = 4_000
private const val RELEASE_NOTES_MAX_CHARS: Int = 500
private const val SPORTS_CATEGORY: String = "SPORTS"
private const val EXPECTED_DEFAULT_LANGUAGE: String = "en-US"

/**
 * JVM regression test for Play Store metadata draft (T234).
 *
 * Locks the textual side of the Play Store listing against the
 * Play Store's hard caps so a future copy edit can't silently push
 * the listing over the limit and break the upload at submission
 * time. The Play Store rejects listings that exceed any of the
 * caps below — there's no graceful truncation and no warning until
 * the upload is in flight.
 *
 * Layout follows gradle-play-publisher's convention so a future
 * `gradle-play-publisher` plugin install can pick this directory
 * up unchanged.
 *
 * What this test does NOT cover (genuinely device-required, hence
 * the [PENDING: device-required] marks in the per-asset NOTES files):
 *  - Phone screenshots (need a running device + active session for
 *    realistic in-session UI captures).
 *  - Tablet screenshots (same).
 *  - High-res app icon + feature graphic — gated on T233 design work.
 *
 * What it DOES cover (the listing-text regression surface):
 *  - Every required metadata file exists under `play/`.
 *  - Title ≤ 30 chars (Play Store hard cap).
 *  - Short description ≤ 80 chars.
 *  - Full description ≤ 4000 chars.
 *  - Release notes ≤ 500 chars.
 *  - default-language.txt declares en-US.
 *  - Category is on the Play Console allow-list.
 *
 * The test reads files relative to the gradle test-task working dir
 * (`app/`), so paths are `src/main/play/...`. Both flavours
 * (devDebug + prodDebug) use the same metadata — the dev build
 * isn't published.
 */
class PlayStoreMetadataTest {
    @Test
    fun default_language_file_exists_and_declares_en_us_so_play_store_listing_resolves_correctly() {
        val file = playFile("default-language.txt")

        assertTrue("missing $file", file.exists())
        assertEquals(EXPECTED_DEFAULT_LANGUAGE, file.readText().trim())
    }

    @Test
    fun contact_email_file_exists_and_is_non_blank_so_play_store_required_contact_field_resolves() {
        val file = playFile("contact-email.txt")

        assertTrue("missing $file", file.exists())
        assertTrue("contact-email.txt must be non-blank", file.readText().trim().isNotEmpty())
    }

    @Test
    fun title_file_exists_and_stays_within_play_store_30_char_hard_cap() {
        val file = listingFile("title.txt")
        assertTrue("missing $file", file.exists())

        val title = file.readText().trim()

        assertTrue(
            "title.txt must be non-blank",
            title.isNotEmpty(),
        )
        assertTrue(
            "title.txt is ${title.length} chars; Play Store hard-caps at $TITLE_MAX_CHARS",
            title.length <= TITLE_MAX_CHARS,
        )
    }

    @Test
    fun short_description_file_exists_and_stays_within_play_store_80_char_hard_cap() {
        val file = listingFile("short-description.txt")
        assertTrue("missing $file", file.exists())

        val short = file.readText().trim()

        assertTrue(
            "short-description.txt must be non-blank",
            short.isNotEmpty(),
        )
        assertTrue(
            "short-description.txt is ${short.length} chars; Play Store hard-caps at $SHORT_DESCRIPTION_MAX_CHARS",
            short.length <= SHORT_DESCRIPTION_MAX_CHARS,
        )
    }

    @Test
    fun full_description_file_exists_and_stays_within_play_store_4000_char_hard_cap() {
        val file = listingFile("full-description.txt")
        assertTrue("missing $file", file.exists())

        val full = file.readText().trim()

        assertTrue(
            "full-description.txt must be non-blank",
            full.isNotEmpty(),
        )
        assertTrue(
            "full-description.txt is ${full.length} chars; Play Store hard-caps at $FULL_DESCRIPTION_MAX_CHARS",
            full.length <= FULL_DESCRIPTION_MAX_CHARS,
        )
    }

    @Test
    fun release_notes_file_exists_and_stays_within_play_store_500_char_hard_cap() {
        val file = playFile("release-notes/$LISTING_LANGUAGE/default.txt")
        assertTrue("missing $file", file.exists())

        val notes = file.readText().trim()

        assertTrue(
            "release-notes/$LISTING_LANGUAGE/default.txt must be non-blank",
            notes.isNotEmpty(),
        )
        assertTrue(
            "release-notes is ${notes.length} chars; Play Store hard-caps at $RELEASE_NOTES_MAX_CHARS",
            notes.length <= RELEASE_NOTES_MAX_CHARS,
        )
    }

    @Test
    fun app_category_file_exists_and_uses_a_value_on_the_play_console_allow_list() {
        val file = playFile("app-category.txt")
        assertTrue("missing $file", file.exists())

        val category = file.readText().trim()

        // Play Console categories — picked the subset relevant to this
        // app's domain. Adding a new category here is a deliberate
        // product decision; defaulting to the wide Play Console list
        // would let a typo (`SPORT` vs `SPORTS`) sneak through.
        assertTrue(
            "category '$category' must be on the Play Console allow-list (currently only SPORTS for this app)",
            category in setOf(SPORTS_CATEGORY),
        )
    }

    private fun playFile(relativePath: String): File = File("$PLAY_DIR/$relativePath")
    private fun listingFile(relativePath: String): File = File("$PLAY_DIR/listings/$LISTING_LANGUAGE/$relativePath")
}
