package com.lap.mobile.release

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val DASHBOARD_KERNEL_DIR: String = "src/main/kotlin/com/lap/mobile/dashboard"
private const val DASHBOARD_UI_DIR: String = "src/main/kotlin/com/lap/mobile/ui/dashboard"
private const val STRINGS_XML_PATH: String = "src/main/res/values/strings.xml"

/**
 * JVM compliance manifest for T191 (instrumented sibling: Compose
 * mockup of live dashboard with speed gauge + lap timing + trackmap
 * — Figma reviewed).
 *
 * The Figma mockup itself is genuinely designer-required and stays
 * `[PENDING: design-required]` for the instrumented half. What this
 * manifest locks at the JVM level is the *shared design-code
 * vocabulary* the eventual Figma mockup must reference: composable
 * names, kernel class names, UiState field names, and strings.xml
 * label keys. Without this lock, designer + developer can drift
 * independently and the mockup becomes outdated as soon as a
 * refactor renames any widget.
 *
 * The scaffold widgets ARE shipped (T192 DashboardScreen+ViewModel,
 * T193 SpeedGauge+SpeedZoneClassifier kernel, T194 LapDisplay+
 * LapTimeFormatter kernel, T195 TrackMap+TrackProjector kernel),
 * each with @Preview fixtures + JVM-tested kernel logic. T191's
 * Figma mockup will refine the visual hierarchy these placeholders
 * carry; this manifest ensures the names stay stable so designer
 * references survive code refactoring.
 *
 * Test strategy — path-existence + content-keyword checks rather
 * than reflection (the composable functions can't be reflected
 * since `@Composable` annotation has SOURCE retention):
 *  - Kernel classes (data class, enum, fun-interface, regular class)
 *    have RUNTIME annotation retention so Class.forName() works.
 *    Exercised in [T240SmokeTestComplianceManifestTest] for the
 *    capture/upload kernels; here we use file-presence + content
 *    keyword for symmetry across both composable + kernel surfaces.
 *
 * What this manifest deliberately does NOT cover:
 *  - The actual Figma file content (visual hierarchy, color choices,
 *    typography decisions, glyph design) — designer-required.
 *  - Whether the placeholder visual choices in the scaffolds match
 *    what the eventual mockup will specify — designer-driven, the
 *    scaffolds are explicitly placeholders that will be refined.
 *  - Pixel-perfect rendering match between mockup + composable —
 *    needs Compose snapshot testing (paparazzi) which adds toolchain
 *    weight; visual review by designer + reviewer covers this.
 */
class T191DashboardWidgetVocabularyManifestTest {
    @Test
    fun dashboard_top_level_composable_and_view_model_files_exist_so_figma_mockup_has_navigation_anchor() {
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_UI_DIR/DashboardScreen.kt",
            description = "T192 DashboardScreen — top-level Composable the Figma mockup will refine",
        )
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_KERNEL_DIR/DashboardViewModel.kt",
            description = "T192 DashboardViewModel — @HiltViewModel exposing StateFlow<DashboardUiState>",
        )
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_KERNEL_DIR/DashboardUiState.kt",
            description = "T192 DashboardUiState data class — the shape Figma mockup field references must match",
        )
    }

    @Test
    fun dashboard_ui_state_field_names_lock_the_data_vocabulary_figma_mockup_must_reference() {
        // The Figma mockup will annotate widgets with data-source
        // labels like "currentSpeedKmh → speed gauge". Locking these
        // field names here means the designer's annotations don't
        // become stale when a refactor renames `currentSpeedKmh` to
        // `speedKmh`.
        val uiStateSource = readResource("$DASHBOARD_KERNEL_DIR/DashboardUiState.kt")
        for (fieldName in EXPECTED_UI_STATE_FIELDS) {
            assertTrue(
                "DashboardUiState must declare field $fieldName — locked for Figma mockup data-source labels",
                uiStateSource.contains("val $fieldName"),
            )
        }
    }

    @Test
    fun speed_gauge_widget_with_speed_zone_kernel_files_exist_so_figma_mockup_can_reference_them() {
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_UI_DIR/SpeedGauge.kt",
            description = "T193 SpeedGauge — 220dp circular ring composable, headline visual",
        )
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_KERNEL_DIR/SpeedZone.kt",
            description = "T193 SpeedZone enum + classifier — Cool/Cruise/Fast/Redline 4 bands",
        )
    }

    @Test
    fun lap_display_widget_with_lap_time_formatter_kernel_files_exist_so_figma_mockup_can_reference_them() {
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_UI_DIR/LapDisplay.kt",
            description = "T194 LapDisplay — lap counter + current-lap timer side-by-side",
        )
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_KERNEL_DIR/LapTimeFormatter.kt",
            description = "T194 LapTimeFormatter — formats Long ms → 'M:SS.mmm' with Locale.US",
        )
    }

    @Test
    fun track_map_widget_with_track_projector_kernel_files_exist_so_figma_mockup_can_reference_them() {
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_UI_DIR/TrackMap.kt",
            description = "T195 TrackMap — Canvas-based GPS-trail breadcrumb, 280×200dp",
        )
        assertWidgetFileExists(
            relativePath = "$DASHBOARD_KERNEL_DIR/TrackProjector.kt",
            description = "T195 TrackProjector — pure-Kotlin lat/lon → canvas-pixel projection",
        )
    }

    @Test
    fun dashboard_strings_lock_label_vocabulary_figma_mockup_can_reference_for_copy_decks() {
        // Figma mockups carry copy decks (the actual text shown to
        // user). Locking string keys here means the designer's
        // mockup-to-string-key mapping survives copy refresh — if
        // the dashboard label "Lap" changes to "Tour", only the
        // string value changes, the key + Figma reference stay stable.
        val stringsXml = readResource(STRINGS_XML_PATH)
        for (stringKey in EXPECTED_DASHBOARD_STRING_KEYS) {
            assertTrue(
                "strings.xml must declare <string name=\"$stringKey\"> — locked for Figma copy-deck mapping",
                stringsXml.contains("name=\"$stringKey\""),
            )
        }
    }

    @Test
    fun dashboard_kernel_directory_contains_documented_widget_kernels_so_directory_layout_is_stable() {
        // Locks the package layout: kernel logic in
        // `com.lap.mobile.dashboard.*`, composables in
        // `com.lap.mobile.ui.dashboard.*`. A future refactor that
        // moved everything into a single `dashboard/` package would
        // break a designer's references like "see kernel @
        // dashboard/SpeedZone.kt".
        val kernelDir = resolveResourceDir(DASHBOARD_KERNEL_DIR)
        val uiDir = resolveResourceDir(DASHBOARD_UI_DIR)
        assertNotNull("kernel directory missing: $DASHBOARD_KERNEL_DIR", kernelDir)
        assertNotNull("UI directory missing: $DASHBOARD_UI_DIR", uiDir)
        assertTrue("kernel directory must contain at least 5 widget files", (kernelDir!!.listFiles()?.size ?: 0) >= 5)
        assertTrue("UI directory must contain at least 4 widget files", (uiDir!!.listFiles()?.size ?: 0) >= 4)
    }

    private fun assertWidgetFileExists(relativePath: String, description: String) {
        val file = resolveResourceFile(relativePath)
        assertTrue(
            "T191 widget vocabulary missing: $relativePath\nContract: $description\n" +
                "If this widget was renamed/moved, the Figma mockup's reference is now stale — " +
                "update both this manifest and the design system documentation.",
            file.exists(),
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

    private fun resolveResourceDir(relativePath: String): File? {
        val primary = File(relativePath)
        if (primary.isDirectory) return primary
        val fallback = File("app/$relativePath")
        if (fallback.isDirectory) return fallback
        return null
    }

    private fun readResource(relativePath: String): String = resolveResourceFile(relativePath).readText()

    private companion object {
        val EXPECTED_UI_STATE_FIELDS: List<String> = listOf(
            "currentSpeedKmh",
            "currentLapNumber",
            "currentLapElapsedMs",
            "recordingStripState",
        )
        val EXPECTED_DASHBOARD_STRING_KEYS: List<String> = listOf(
            "dashboard_speed_kmh",
            "dashboard_lap_number",
            "dashboard_lap_elapsed_ms",
            "dashboard_status_recording",
            "dashboard_status_idle",
            "speed_gauge_unit_kmh",
            "lap_display_label",
            "lap_display_number_format",
        )
    }
}
