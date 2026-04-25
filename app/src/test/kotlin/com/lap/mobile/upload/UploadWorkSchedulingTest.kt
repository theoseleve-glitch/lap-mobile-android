package com.lap.mobile.upload

import androidx.work.ExistingWorkPolicy
import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SESSION_A: UUID7 = "01997333-9d2e-7000-8000-00000000000a"
private const val SESSION_B: UUID7 = "01997333-9d2e-7000-8000-00000000000b"

class UploadWorkSchedulingTest {
    @Test
    fun unique_work_name_for_a_session_is_deterministic_so_a_second_enqueue_collides_with_the_first() {
        // Worker bound to UNIQUE by session_id (T159). Re-enqueueing the
        // same session must produce the same name so WorkManager treats
        // the second call as a duplicate of the first — the user can hit
        // the upload button twice without spawning two workers.
        val first = UploadWorkScheduling.uniqueWorkNameFor(SESSION_A)
        val second = UploadWorkScheduling.uniqueWorkNameFor(SESSION_A)

        assertEquals(first, second)
    }

    @Test
    fun unique_work_name_includes_the_session_id_so_two_sessions_run_in_parallel_under_distinct_names() {
        val nameA = UploadWorkScheduling.uniqueWorkNameFor(SESSION_A)
        val nameB = UploadWorkScheduling.uniqueWorkNameFor(SESSION_B)

        assertNotEquals(nameA, nameB)
        assertTrue("name for SESSION_A must contain its id, got '$nameA'", SESSION_A in nameA)
        assertTrue("name for SESSION_B must contain its id, got '$nameB'", SESSION_B in nameB)
    }

    @Test
    fun unique_work_name_is_prefixed_so_it_does_not_collide_with_other_workers_in_the_app() {
        // Other workers in the project (BLE pairing, future recording-side
        // work) will mint their own unique names — pinning a stable
        // "upload-bundle-" prefix means the namespaces never overlap.
        val name = UploadWorkScheduling.uniqueWorkNameFor(SESSION_A)

        assertTrue(
            "upload work name must start with 'upload-bundle-' to namespace it, got '$name'",
            name.startsWith("upload-bundle-"),
        )
    }

    @Test
    fun existing_work_policy_is_keep_so_a_pending_or_running_worker_is_not_replaced_by_a_duplicate_enqueue() {
        // KEEP semantic: if a worker for this session is ENQUEUED, RUNNING,
        // or BLOCKED, the new request is ignored (no duplicate). REPLACE
        // would cancel the in-flight worker and restart — discarding any
        // T152 chunks_sent progress. APPEND would queue another after,
        // also a duplicate. Only KEEP matches the no-duplicates contract.
        assertEquals(ExistingWorkPolicy.KEEP, UploadWorkScheduling.existingWorkPolicy)
    }
}
