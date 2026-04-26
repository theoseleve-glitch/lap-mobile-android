package com.lap.mobile.capture

import com.lap.mobile.core.DomainError
import com.lap.mobile.core.Result
import com.lap.mobile.data.FakeJournalDao
import com.lap.mobile.data.FakeSessionDao
import com.lap.mobile.data.FixedClock
import com.lap.mobile.data.JournalWriter
import com.lap.mobile.data.SessionCoordinator
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val SESSION_ID: UUID7 = "01997333-9d2e-7000-8000-000000000229"
private val FIXED_NOW: Instant = Instant.parse("2026-04-26T12:00:00Z")
private const val GPS_GOOD_ACCURACY_M: Float = 3.5f
private const val GPS_GOOD_SAT_COUNT: Int = 9
private const val BATTERY_HEALTHY_PERCENT: Int = 80
private const val THERMAL_NOMINAL: Int = 0
private const val SDK_INT_API_33: Int = 33
private const val PLENTY_OF_FREE_BYTES: Long = 10L * 1024L * 1024L * 1024L
private const val LITTLE_FREE_STORAGE_BYTES: Long = 1L * 1024L * 1024L
private const val FAKE_TRACK_ID: String = "track-paul-ricard"
private const val LOW_BATTERY_PERCENT: Int = 5
private const val SEVERE_THERMAL: Int = 4

/**
 * JVM integration test for the preflight hard-block scenarios (T229
 * — the device-required half is marked PENDING in the sprint).
 *
 * Stitches together the real production components:
 *  - [SessionCoordinator] — the consumer that gates
 *    ARMED → RECORDING on `preflight.check()` (T077).
 *  - [DefaultPreflightService] — aggregates every individual
 *    [PreflightCheck] into one [PreflightStatus] (T086).
 *  - The full set of registered hard-block + soft-warn checks:
 *    [LocationPermissionCheck] (T087) + [NotificationPermissionCheck]
 *    (T088) + [BluetoothCheck] (T089) + [TrackSelectionCheck] (T090)
 *    + [GpsFixQualityCheck] (T091) + [RaceboxConnectionCheck] (T092)
 *    + [StorageAvailabilityCheck] (T093) + [BatteryLevelCheck] (T094)
 *    + [ThermalStateCheck] (T095).
 *
 * Differs from the per-check unit tests: those test each check in
 * isolation against a single fake. This test exercises the FULL
 * chain — from a failing subsystem state through the check, the
 * service aggregation, and into [SessionCoordinator.start]'s
 * `Result.Err` shape that the UI layer (T097) renders. A future
 * refactor that drops the gate, swaps the aggregation order, or
 * loses a reason on its way through should break here.
 *
 * What this test does NOT cover (genuinely device-required, hence
 * the [PENDING: device-required] mark in the sprint file):
 *  - Real Android [android.Manifest.permission] grants via the
 *    runtime-permission Activity Result Contract.
 *  - Real `BluetoothManager.adapter.isEnabled` plus the OS
 *    enable-BT prompt flow.
 *  - Real `LocationManager` + `GnssStatus.Callback` snapshot
 *    semantics.
 *  - Real `ThermalManager.PowerSaveMode` events surfaced through
 *    the OS.
 *
 * What it DOES cover (the hard-block regression surface):
 *  - All-passing chain → [SessionCoordinator.start] proceeds to
 *    RECORDING.
 *  - Each individual hard-block reason propagates to the error
 *    message verbatim (UI remediation routing depends on the exact
 *    reason vocabulary).
 *  - Multiple hard-blocks aggregate in check-registration order.
 *  - Soft-warn-only failures still return [Result.Err] at the
 *    coordinator level (the gate is coarse), but
 *    [PreflightStatus.canStart] returns true so the UI layer can
 *    enable the Start button regardless — the integration tests
 *    the dual shape, not just one side.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreflightHardBlockScenariosTest {
    @Test
    fun all_checks_passing_lets_session_advance_from_armed_to_recording() = runTest {
        val rig = PreflightRig()
        val coord = newCoordinator(rig)
        coord.arm(SESSION_ID)

        // backgroundScope (not `this`) so the capture/journal jobs
        // launched by start() are torn down at runTest exit; otherwise
        // runTest blocks on uncompleted children. Mirrors the pattern
        // in SessionCoordinatorTest.start_with_passing_preflight_*.
        val result = coord.start(scope = backgroundScope)

        assertTrue("expected Ok, got $result", result is Result.Ok)
        coord.stop()
    }

    @Test
    fun denied_location_permission_blocks_start_with_location_permission_in_error_message() = runTest {
        val rig = PreflightRig().apply { locationForegroundGranted = false }
        val coord = newCoordinator(rig)
        coord.arm(SESSION_ID)

        val result = coord.start(scope = backgroundScope)

        assertPreflightErr(result, expectedReason = LocationPermissionCheck.REASON_LOCATION_PERMISSION)
    }

    @Test
    fun gps_no_fix_blocks_start_with_gps_no_fix_in_error_message() = runTest {
        val rig = PreflightRig().apply { gpsAccuracyMeters = null }
        val coord = newCoordinator(rig)
        coord.arm(SESSION_ID)

        val result = coord.start(scope = backgroundScope)

        assertPreflightErr(result, expectedReason = GpsFixQualityCheck.REASON_GPS_NO_FIX)
    }

    @Test
    fun bluetooth_disabled_in_racebox_mode_blocks_start_with_bluetooth_disabled_in_error_message() = runTest {
        val rig = PreflightRig().apply {
            raceboxModeEnabled = true
            // Permission granted but adapter off — exercises the second
            // failure-mode in BluetoothCheck (permission > adapter
            // priority).
            bluetoothEnabled = false
        }
        val coord = newCoordinator(rig)
        coord.arm(SESSION_ID)

        val result = coord.start(scope = backgroundScope)

        assertPreflightErr(result, expectedReason = BluetoothCheck.REASON_BLUETOOTH_DISABLED)
    }

    @Test
    fun multiple_hard_block_failures_aggregate_all_reasons_in_check_registration_order() = runTest {
        // Track is dropped + GPS has no fix + storage is low. Three
        // hard-blocks. The order in the error message must follow the
        // order checks were registered (DefaultPreflightService
        // documents the contract); a future Hilt-binding reshuffle that
        // changes the registration order would break this test.
        val rig = PreflightRig().apply {
            trackSelected = false
            gpsAccuracyMeters = null
            freeStorageBytes = LITTLE_FREE_STORAGE_BYTES
        }
        val coord = newCoordinator(rig)
        coord.arm(SESSION_ID)

        val result = coord.start(scope = backgroundScope)

        val message = assertResultErrPreflight(result).message
        // All three reasons must surface; the order locks the
        // registration order in checksFor().
        val trackIdx = message.indexOf(TrackSelectionCheck.REASON_TRACK_NOT_SELECTED)
        val gpsIdx = message.indexOf(GpsFixQualityCheck.REASON_GPS_NO_FIX)
        val storageIdx = message.indexOf(StorageAvailabilityCheck.REASON_STORAGE_LOW)
        assertTrue("track reason missing from $message", trackIdx >= 0)
        assertTrue("gps reason missing from $message", gpsIdx >= 0)
        assertTrue("storage reason missing from $message", storageIdx >= 0)
        assertTrue(
            "expected order: track ($trackIdx) < gps ($gpsIdx) < storage ($storageIdx) in '$message'",
            trackIdx < gpsIdx && gpsIdx < storageIdx,
        )
    }

    @Test
    fun soft_warns_alone_block_at_coordinator_level_but_canstart_filters_them_so_the_ui_can_enable_start() = runTest {
        // Headline T229 dual-shape invariant: SessionCoordinator's
        // gate is coarse — ANY NotReady (soft warns included) returns
        // an Err. The UI layer (T097) bridges that to the Start button
        // via PreflightStatus.canStart() which filters soft warns out.
        // Both consumers MUST see the same status object so a future
        // refactor can't drift them apart.
        val rig = PreflightRig().apply {
            batteryPercent = LOW_BATTERY_PERCENT
            thermalLevel = SEVERE_THERMAL
        }
        val service = DefaultPreflightService(checks = rig.buildChecks())

        val status = service.check()

        assertTrue("expected NotReady, got $status", status is PreflightStatus.NotReady)
        val notReady = status as PreflightStatus.NotReady
        // Both reasons are soft warns — neither is hard.
        assertFalse(
            "battery_low must be soft",
            PreflightSeverity.isHardBlock(BatteryLevelCheck.REASON_BATTERY_LOW),
        )
        assertFalse(
            "thermal_high must be soft",
            PreflightSeverity.isHardBlock(ThermalStateCheck.REASON_THERMAL_HIGH),
        )
        // canStart() returns true → UI Start button enabled.
        assertTrue("soft-only NotReady must allow canStart", notReady.canStart())

        // Coordinator gate stays coarse — anything-NotReady → Err.
        val coord = newCoordinator(rig)
        coord.arm(SESSION_ID)
        val result = coord.start(scope = backgroundScope)
        assertTrue("coordinator gate is coarse; expected Err, got $result", result is Result.Err)
    }

    @Test
    fun mixed_hard_and_soft_reasons_block_session_start_and_disable_ui_start_via_canstart() = runTest {
        val rig = PreflightRig().apply {
            // A soft warn (battery) AND a hard block (no gps fix). The
            // hard block should drive canStart() to false regardless of
            // the soft warn.
            batteryPercent = LOW_BATTERY_PERCENT
            gpsAccuracyMeters = null
        }
        val service = DefaultPreflightService(checks = rig.buildChecks())

        val status = service.check()

        assertTrue("expected NotReady, got $status", status is PreflightStatus.NotReady)
        val notReady = status as PreflightStatus.NotReady
        assertFalse(
            "any hard-block in the list disables canStart regardless of soft warns",
            notReady.canStart(),
        )

        // And the coordinator surfaces the hard reason in the error message.
        val coord = newCoordinator(rig)
        coord.arm(SESSION_ID)
        val result = coord.start(scope = backgroundScope)
        val err = assertResultErrPreflight(result)
        assertTrue(
            "gps_no_fix must be in the error message: ${err.message}",
            GpsFixQualityCheck.REASON_GPS_NO_FIX in err.message,
        )
    }

    private fun newCoordinator(rig: PreflightRig): SessionCoordinator {
        val service: PreflightService = DefaultPreflightService(checks = rig.buildChecks())
        val orchestrator = CaptureOrchestrator(
            location = LocationCaptureService(FixedLocationDataSource(emptyList())),
            motion = MotionCaptureService(FixedMotionDataSource(emptyList())),
        )
        val journalWriter = JournalWriter(dao = FakeJournalDao(), clock = FixedClock(FIXED_NOW))
        return SessionCoordinator(
            sessionDao = FakeSessionDao(),
            orchestrator = orchestrator,
            journalWriter = journalWriter,
            clock = FixedClock(FIXED_NOW),
            preflight = service,
        )
    }

    private fun assertPreflightErr(result: Result<Unit, DomainError>, expectedReason: String) {
        val err = assertResultErrPreflight(result)
        assertTrue(
            "expected '$expectedReason' in error message, got: ${err.message}",
            expectedReason in err.message,
        )
    }

    private fun assertResultErrPreflight(result: Result<Unit, DomainError>): DomainError.Validation {
        assertTrue("expected Result.Err, got $result", result is Result.Err)
        val error = (result as Result.Err).error
        assertTrue("expected DomainError.Validation, got $error", error is DomainError.Validation)
        val validation = error as DomainError.Validation
        // SessionCoordinator stamps field="preflight" so the consumer can
        // route validation errors to the preflight remediation surface
        // rather than the form-level message bar.
        assertEquals("preflight", validation.field)
        return validation
    }
}

/**
 * Tunable rig assembling all production [PreflightCheck]s on top of
 * trivial fun-interface fakes. Every knob defaults to the
 * "all-passing" state so a test only has to flip the one(s) it
 * cares about — mirrors the BatterySaverPolicy / ThermalAwarePolicy
 * test rigs already in the codebase.
 */
private class PreflightRig {
    var locationForegroundGranted: Boolean = true
    var locationBackgroundGranted: Boolean = true
    var notificationGranted: Boolean = true
    var bluetoothPermissionGranted: Boolean = true
    var bluetoothEnabled: Boolean = true
    var raceboxModeEnabled: Boolean = false
    var raceboxConnected: Boolean = true
    var trackSelected: Boolean = true
    var gpsAccuracyMeters: Float? = GPS_GOOD_ACCURACY_M
    var gpsSatelliteCount: Int = GPS_GOOD_SAT_COUNT
    var freeStorageBytes: Long = PLENTY_OF_FREE_BYTES
    var batteryPercent: Int = BATTERY_HEALTHY_PERCENT
    var thermalLevel: Int = THERMAL_NOMINAL
    var sdkInt: Int = SDK_INT_API_33

    fun buildChecks(): List<PreflightCheck> {
        val permissionChecker = PermissionChecker { permission ->
            when (permission) {
                LocationPermissionManager.FINE_LOCATION,
                LocationPermissionManager.COARSE_LOCATION,
                -> locationForegroundGranted
                LocationPermissionManager.BACKGROUND_LOCATION -> locationBackgroundGranted
                NotificationPermissionCheck.POST_NOTIFICATIONS -> notificationGranted
                BluetoothCheck.BLUETOOTH_CONNECT -> bluetoothPermissionGranted
                else -> error("unexpected permission queried by check chain: $permission")
            }
        }
        // T229 registration order MUST match what the test
        // `multiple_hard_block_failures_aggregate_all_reasons_in_check_registration_order`
        // asserts (track → gps → storage). Keeping it documented here
        // so a future re-ordering breaks both this rig and that test
        // together.
        return listOf(
            LocationPermissionCheck(LocationPermissionManager(permissionChecker, sdkInt)),
            NotificationPermissionCheck(checker = permissionChecker, sdkInt = sdkInt),
            BluetoothCheck(
                checker = permissionChecker,
                adapter = BluetoothAdapterStatus { bluetoothEnabled },
                sdkInt = sdkInt,
                raceboxModeEnabled = { raceboxModeEnabled },
            ),
            TrackSelectionCheck(SelectedTrackProvider { if (trackSelected) FAKE_TRACK_ID else null }),
            GpsFixQualityCheck(
                provider = GpsQualityProvider {
                    GpsQualitySnapshot(
                        accuracyMeters = gpsAccuracyMeters,
                        satelliteCount = gpsSatelliteCount,
                    )
                },
            ),
            RaceboxConnectionCheck(
                status = RaceboxConnectionStatus { raceboxConnected },
                raceboxModeEnabled = { raceboxModeEnabled },
            ),
            StorageAvailabilityCheck(StorageStatus { freeStorageBytes }),
            BatteryLevelCheck(BatteryStatus { batteryPercent }),
            ThermalStateCheck(ThermalStatusReader { thermalLevel }),
        )
    }
}

@Suppress("unused")
private fun TestScope.assertScopeNonNull() {
    // Stub-anchor so the @OptIn import isn't unused in the bytecode if
    // the test body ever drops its TestScope reference; suppresses the
    // detekt unused-suppress warning that would otherwise greenfield.
    requireNotNull(this)
}
