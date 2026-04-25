package com.lap.mobile.auth

import com.lap.mobile.upload.AuthState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks T183 contract: capture is local-first and doesn't gate on
 * auth; uploads gate at attempt time.
 *
 * The two halves live as separate predicates so the consumer
 * (preflight readiness check, upload work scheduler) can ask exactly
 * the question it needs:
 *   - The recording-side preflight (T096+) reads
 *     [permitsRecordingStart] and must NEVER add an "is paired"
 *     check. The whole point is a driver who lost their phone session
 *     in the pit can still capture the next outing.
 *   - The upload-side scheduler reads [permitsUploadAttempt] before
 *     enqueueing a worker. Without it, an Anonymous session would
 *     spin the worker, get 401, and re-cycle — wasted battery /
 *     bandwidth / log noise. T166's AuthRestoreKickPolicy is what
 *     wakes the queue once auth flips back to Authenticated.
 *
 * Together with T166 (kick-on-auth-restore) these encode the full
 * "captures pile up locally, drain when paired" contract.
 */
class OfflineCapturePolicyTest {
    @Test
    fun anonymous_session_permits_recording_start_so_unpaired_driver_doesnt_lose_an_outing() {
        // Headline T183 invariant: the value of offline-first capture
        // is exactly that an Anonymous user can still record. Adding
        // an auth gate to preflight would defeat the entire feature.
        assertTrue(OfflineCapturePolicy.permitsRecordingStart(AuthState.Anonymous))
    }

    @Test
    fun authenticated_session_also_permits_recording_start() {
        // Same answer regardless of auth — recording is local-only,
        // not an authed action.
        assertTrue(OfflineCapturePolicy.permitsRecordingStart(AuthState.Authenticated))
    }

    @Test
    fun anonymous_session_does_not_permit_upload_attempt_so_we_dont_burn_a_401_round_trip() {
        // If the scheduler tried to upload while Anonymous: AuthInterceptor
        // (T149) forwards without bearer, server returns 401, the row
        // stays queued. That's correct end-state but wastes battery and
        // bandwidth on every kick. Gating at attempt time means we
        // simply don't enqueue the worker.
        assertFalse(OfflineCapturePolicy.permitsUploadAttempt(AuthState.Anonymous))
    }

    @Test
    fun authenticated_session_permits_upload_attempt_so_queued_rows_drain() {
        assertTrue(OfflineCapturePolicy.permitsUploadAttempt(AuthState.Authenticated))
    }
}
