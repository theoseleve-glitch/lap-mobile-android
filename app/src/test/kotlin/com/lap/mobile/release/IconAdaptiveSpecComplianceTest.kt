package com.lap.mobile.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val FOREGROUND_PATH: String = "src/main/res/drawable/ic_launcher_foreground.xml"
private const val ADAPTIVE_MANIFEST_PATH: String = "src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
private const val ADAPTIVE_ROUND_MANIFEST_PATH: String = "src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"
private const val COLORS_PATH: String = "src/main/res/values/colors.xml"

private const val EXPECTED_VECTOR_DIMENSION_DP: String = "108dp"
private const val EXPECTED_VIEWPORT_DIMENSION: String = "108"
private const val EXPECTED_BRAND_BACKGROUND_COLOR: String = "#1565C0"
private const val BACKGROUND_COLOR_RESOURCE_NAME: String = "ic_launcher_background"

/**
 * JVM compliance manifest for T233 (instrumented sibling: replace
 * the placeholder app-icon with the final brand-designed logo).
 *
 * The actual icon REPLACEMENT is genuinely designer-required —
 * needs Figma/Illustrator artwork + brand-color/typography
 * decisions + stakeholder approval. That stays
 * `[PENDING: design-required]` for the instrumented half.
 *
 * What this manifest locks at the JVM level is the *contract* any
 * future designer-supplied icon must satisfy:
 *  - Android adaptive-icon viewport dimensions (108dp x 108dp).
 *  - Adaptive-icon manifest XML structure (foreground + background
 *    drawable references) so the launcher can apply circle / squircle
 *    / rounded-square masks across device manufacturers.
 *  - Brand background color matches the documented brand-blue
 *    (#1565C0 — Material Design Blue 800) so a stray copy-paste
 *    that drifts the value is caught at build time.
 *
 * Why structure-only and not pixel-snapshot:
 *  - Pixel snapshots would force this test to update every legitimate
 *    icon change, training the team to ignore the test rather than
 *    refine the icon. The structure assertions catch the regressions
 *    that actually matter (wrong dimensions, missing manifest entries,
 *    drifted brand color) without locking visual choices.
 *  - The 66dp safe-zone compliance for foreground content would need
 *    a real SVG path parser (curves + relative coords + arcs); the
 *    placeholder uses simple M/L lines that fit easily, but a
 *    designer's eventual icon could legitimately use curves whose
 *    control points fall outside 21..87 while the visible bounds
 *    stay inside. Visual review by the designer covers this; a
 *    naive heuristic would over-flag.
 *
 * What this test does NOT cover (genuinely design-required):
 *  - The visual brand identity decisions — colors, typography,
 *    glyph shapes, monogram vs full logo, light vs dark adaptation.
 *  - The 66dp safe-zone visual compliance (covered by designer
 *    review against Material Design adaptive-icon guidelines).
 *  - Asset density variants (xxxhdpi etc.) — adaptive icons
 *    deliberately don't need them; the v26+ manifest references
 *    the vector + color and the launcher rasterizes per-device.
 *  - Splash-screen icon (separate Android 12+ surface, not part
 *    of the adaptive-icon contract).
 */
class IconAdaptiveSpecComplianceTest {
    private val foregroundXml: String by lazy { readResource(FOREGROUND_PATH) }
    private val adaptiveManifestXml: String by lazy { readResource(ADAPTIVE_MANIFEST_PATH) }
    private val adaptiveRoundManifestXml: String by lazy { readResource(ADAPTIVE_ROUND_MANIFEST_PATH) }
    private val colorsXml: String by lazy { readResource(COLORS_PATH) }

    @Test
    fun foreground_drawable_file_exists_and_is_non_empty() {
        val file = resolveResourceFile(FOREGROUND_PATH)
        assertTrue("foreground drawable file missing at $file (resolved: ${file.absolutePath})", file.exists())
        assertTrue("foreground drawable file must not be empty", file.length() > 0)
    }

    @Test
    fun foreground_vector_declares_108dp_width_per_android_adaptive_icon_spec() {
        // Adaptive icons on Android 8+ are sized to a 108dp canvas
        // regardless of device density. A future icon shipped at
        // 72dp / 96dp / 192dp would render incorrectly across launcher
        // masks. Locks the spec dimension at build time.
        assertTrue(
            "foreground vector must declare android:width=\"$EXPECTED_VECTOR_DIMENSION_DP\" " +
                "per Android adaptive-icon spec",
            foregroundXml.contains("android:width=\"$EXPECTED_VECTOR_DIMENSION_DP\""),
        )
    }

    @Test
    fun foreground_vector_declares_108dp_height_per_android_adaptive_icon_spec() {
        assertTrue(
            "foreground vector must declare android:height=\"$EXPECTED_VECTOR_DIMENSION_DP\" " +
                "per Android adaptive-icon spec",
            foregroundXml.contains("android:height=\"$EXPECTED_VECTOR_DIMENSION_DP\""),
        )
    }

    @Test
    fun foreground_vector_declares_108_viewport_width_so_path_coords_map_directly_to_dp() {
        // Locks the 1:1 mapping of path coords to the 108dp canvas —
        // required for the safe-zone math (66dp safe zone = path
        // coords 21..87 along each axis) to be reasoned about by
        // designer + reviewer + this test alike.
        assertTrue(
            "foreground vector must declare android:viewportWidth=\"$EXPECTED_VIEWPORT_DIMENSION\"",
            foregroundXml.contains("android:viewportWidth=\"$EXPECTED_VIEWPORT_DIMENSION\""),
        )
    }

    @Test
    fun foreground_vector_declares_108_viewport_height_so_path_coords_map_directly_to_dp() {
        assertTrue(
            "foreground vector must declare android:viewportHeight=\"$EXPECTED_VIEWPORT_DIMENSION\"",
            foregroundXml.contains("android:viewportHeight=\"$EXPECTED_VIEWPORT_DIMENSION\""),
        )
    }

    @Test
    fun adaptive_icon_manifest_references_both_foreground_drawable_and_background_color() {
        // Without both layer references the launcher renders an empty
        // square (or worse, falls back to the default Android robot
        // icon). The mipmap-anydpi-v26 layer split is what makes
        // adaptive icons adaptive — single-drawable launchers can't
        // mask an icon that doesn't have separate fg/bg layers.
        assertTrue(
            "adaptive-icon manifest must reference foreground drawable",
            adaptiveManifestXml.contains("@drawable/ic_launcher_foreground"),
        )
        assertTrue(
            "adaptive-icon manifest must reference background color",
            adaptiveManifestXml.contains("@color/$BACKGROUND_COLOR_RESOURCE_NAME"),
        )
    }

    @Test
    fun adaptive_icon_round_manifest_references_both_foreground_drawable_and_background_color() {
        // Some launchers (older Pixel, Samsung One UI) request the
        // _round adaptive icon explicitly. Without the round manifest
        // those launchers fall back to a non-round legacy icon, which
        // looks broken next to first-party apps that ship the round
        // variant.
        assertTrue(
            "round adaptive-icon manifest must reference foreground drawable",
            adaptiveRoundManifestXml.contains("@drawable/ic_launcher_foreground"),
        )
        assertTrue(
            "round adaptive-icon manifest must reference background color",
            adaptiveRoundManifestXml.contains("@color/$BACKGROUND_COLOR_RESOURCE_NAME"),
        )
    }

    @Test
    fun background_color_resource_is_defined_in_colors_xml() {
        assertTrue(
            "colors.xml must define <color name=\"$BACKGROUND_COLOR_RESOURCE_NAME\">",
            colorsXml.contains("<color name=\"$BACKGROUND_COLOR_RESOURCE_NAME\""),
        )
    }

    @Test
    fun background_color_value_matches_documented_brand_blue_so_palette_drift_is_caught() {
        // Brand color is Material Design Blue 800 (#1565C0). Documented
        // here both as the expected constant AND in the assertion
        // message so a future drift to e.g. #2196F3 (Blue 500) shows up
        // with both colors in the failure output for quick triage.
        // Designer-supplied final icon will reuse this value or replace
        // it with a documented brand decision; either way an
        // accidental copy-paste drift is caught.
        val brandBlueLine = colorsXml.lineSequence().firstOrNull {
            it.contains(BACKGROUND_COLOR_RESOURCE_NAME) && it.contains("<color")
        }
        assertTrue(
            "could not find <color name=\"$BACKGROUND_COLOR_RESOURCE_NAME\"> line in colors.xml",
            brandBlueLine != null,
        )
        assertEquals(
            "background color must match documented brand-blue $EXPECTED_BRAND_BACKGROUND_COLOR " +
                "(Material Design Blue 800); update both this test AND the brand spec if changing intentionally",
            true,
            brandBlueLine!!.contains(EXPECTED_BRAND_BACKGROUND_COLOR, ignoreCase = true),
        )
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

    private fun readResource(relativePath: String): String = resolveResourceFile(relativePath).readText()
}
