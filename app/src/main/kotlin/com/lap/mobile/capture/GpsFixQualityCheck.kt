package com.lap.mobile.capture

internal const val DEFAULT_GPS_ACCURACY_MAX_METERS: Float = 20.0f
internal const val DEFAULT_GPS_MIN_SATELLITES: Int = 6

/**
 * Preflight check for GNSS fix quality (T091). Hard-blocks recording
 * unless the receiver has a fix with horizontal accuracy ≤
 * [accuracyMaxMeters] AND ≥ [minSatellites] satellites in the fix.
 *
 * Both thresholds default to the consumer-grade smartphone numbers
 * the task spec calls out (≤ 20 m, ≥ 6 sats); they're constructor-
 * injected so a future track-specific override (e.g., karts on a
 * narrow indoor track that need ≤ 5 m) is one Hilt binding away.
 *
 * The "wait" override the task spec mentions is a UI affordance
 * (T097, "let me record anyway") layered above this check —
 * surfacing it here would couple the policy to a single UI mode.
 *
 * Failure-mode priority: no-fix → accuracy → satellites. While the
 * receiver is still warming up there's no actionable signal in
 * "low sat count" or "wide accuracy" — it'll resolve itself once a
 * fix lands. After a fix, "accuracy" comes before "satellites"
 * because the user can move to open sky for both, but accuracy is
 * the more directly measured quality bar.
 */
class GpsFixQualityCheck(
    private val provider: GpsQualityProvider,
    private val accuracyMaxMeters: Float = DEFAULT_GPS_ACCURACY_MAX_METERS,
    private val minSatellites: Int = DEFAULT_GPS_MIN_SATELLITES,
) : PreflightCheck {
    override suspend fun failureReason(): String? {
        val snapshot = provider.snapshot()
        val accuracy = snapshot.accuracyMeters ?: return REASON_GPS_NO_FIX
        if (accuracy > accuracyMaxMeters) return REASON_GPS_ACCURACY
        if (snapshot.satelliteCount < minSatellites) return REASON_GPS_SATELLITES
        return null
    }

    companion object {
        const val REASON_GPS_NO_FIX: String = "gps_no_fix"
        const val REASON_GPS_ACCURACY: String = "gps_accuracy"
        const val REASON_GPS_SATELLITES: String = "gps_satellites"
    }
}
