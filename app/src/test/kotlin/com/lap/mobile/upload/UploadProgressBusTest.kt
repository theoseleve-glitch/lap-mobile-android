package com.lap.mobile.upload

import com.lap.schemas.v1.UUID7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

private const val SESSION_A: UUID7 = "01997333-9d2e-7000-8000-00000000000a"
private const val SESSION_B: UUID7 = "01997333-9d2e-7000-8000-00000000000b"
private const val CHUNKS_TOTAL: Int = 4

class UploadProgressBusTest {
    @Test
    fun reporting_progress_makes_it_visible_under_the_session_id_in_the_state_map() {
        val bus = UploadProgressBus()

        bus.report(UploadProgress(sessionId = SESSION_A, chunksSent = 2, chunksTotal = CHUNKS_TOTAL))

        assertEquals(
            UploadProgress(SESSION_A, chunksSent = 2, chunksTotal = CHUNKS_TOTAL),
            bus.state.value[SESSION_A],
        )
    }

    @Test
    fun reporting_for_one_session_does_not_overwrite_progress_of_another_session() {
        val bus = UploadProgressBus()
        bus.report(UploadProgress(SESSION_A, chunksSent = 1, chunksTotal = CHUNKS_TOTAL))

        bus.report(UploadProgress(SESSION_B, chunksSent = 3, chunksTotal = CHUNKS_TOTAL))

        assertEquals(1, bus.state.value.getValue(SESSION_A).chunksSent)
        assertEquals(3, bus.state.value.getValue(SESSION_B).chunksSent)
    }

    @Test
    fun newer_progress_for_the_same_session_replaces_the_previous_event() {
        val bus = UploadProgressBus()
        bus.report(UploadProgress(SESSION_A, chunksSent = 1, chunksTotal = CHUNKS_TOTAL))

        bus.report(UploadProgress(SESSION_A, chunksSent = 2, chunksTotal = CHUNKS_TOTAL))

        assertEquals(2, bus.state.value.getValue(SESSION_A).chunksSent)
        assertEquals(1, bus.state.value.size)
    }

    @Test
    fun unobserved_session_returns_null_so_the_ui_can_render_a_no_progress_state() {
        val bus = UploadProgressBus()

        assertNull(bus.state.value[SESSION_A])
    }

    @Test
    fun upload_progress_rejects_negative_chunks_sent() {
        assertThrows(IllegalArgumentException::class.java) {
            UploadProgress(SESSION_A, chunksSent = -1, chunksTotal = CHUNKS_TOTAL)
        }
    }

    @Test
    fun upload_progress_rejects_negative_chunks_total() {
        assertThrows(IllegalArgumentException::class.java) {
            UploadProgress(SESSION_A, chunksSent = 0, chunksTotal = -1)
        }
    }

    @Test
    fun upload_progress_rejects_chunks_sent_greater_than_chunks_total_so_off_by_one_bugs_surface() {
        assertThrows(IllegalArgumentException::class.java) {
            UploadProgress(SESSION_A, chunksSent = CHUNKS_TOTAL + 1, chunksTotal = CHUNKS_TOTAL)
        }
    }

    @Test
    fun upload_progress_accepts_chunks_sent_equal_to_chunks_total_to_represent_completion() {
        // Not throwing is the assertion.
        UploadProgress(SESSION_A, chunksSent = CHUNKS_TOTAL, chunksTotal = CHUNKS_TOTAL)
    }
}
