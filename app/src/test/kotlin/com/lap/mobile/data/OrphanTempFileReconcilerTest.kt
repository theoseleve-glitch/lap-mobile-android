package com.lap.mobile.data

import com.lap.schemas.v1.SourceTag
import com.lap.schemas.v1.UUID7
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OrphanTempFileReconcilerTest {
    private val onDiskA: UUID7 = "0194aaaa-aaaa-7aaa-aaaa-aaaaaaaaaaa1"
    private val onDiskB: UUID7 = "0194bbbb-bbbb-7bbb-bbbb-bbbbbbbbbbbb"
    private val onDiskC: UUID7 = "0194cccc-cccc-7ccc-cccc-cccccccccccc"

    @Test
    fun empty_disk_reports_no_orphans() = runTest {
        val reconciler = OrphanTempFileReconciler(
            inventory = FakeTempFileInventory(),
            sessionDao = FakeSessionDao(),
        )
        assertEquals(emptyList<UUID7>(), reconciler.findOrphans())
    }

    @Test
    fun files_with_matching_db_rows_are_not_orphans() = runTest {
        val inventory = FakeTempFileInventory(listOf(onDiskA, onDiskB))
        val dao = FakeSessionDao().apply {
            put(session(onDiskA))
            put(session(onDiskB))
        }
        val reconciler = OrphanTempFileReconciler(inventory, dao)
        assertEquals(emptyList<UUID7>(), reconciler.findOrphans())
    }

    @Test
    fun files_without_matching_db_rows_are_orphans() = runTest {
        val inventory = FakeTempFileInventory(listOf(onDiskA, onDiskB, onDiskC))
        val dao = FakeSessionDao().apply {
            // Only onDiskB has a DB row; A and C are orphans.
            put(session(onDiskB))
        }
        val reconciler = OrphanTempFileReconciler(inventory, dao)
        val orphans = reconciler.findOrphans().toSet()
        assertEquals(setOf(onDiskA, onDiskC), orphans)
        assertTrue(onDiskB !in orphans)
    }

    @Test
    fun db_rows_without_disk_files_are_not_reported() = runTest {
        val inventory = FakeTempFileInventory(emptyList())
        val dao = FakeSessionDao().apply {
            // Session in DB but no temp file — not our concern (orphan DB row
            // cleanup is a separate responsibility, T057).
            put(session(onDiskA))
        }
        val reconciler = OrphanTempFileReconciler(inventory, dao)
        assertEquals(emptyList<UUID7>(), reconciler.findOrphans())
    }

    private fun session(id: UUID7): SessionEntity = SessionEntity(
        id = id,
        state = "recording",
        source = SourceTag.REAL,
        startedAt = Instant.parse("2026-04-24T09:00:00Z"),
    )
}

/** In-memory TempFileInventory for tests. */
private class FakeTempFileInventory(
    private val entries: List<UUID7> = emptyList(),
) : TempFileInventory {
    override suspend fun listSessionIdsOnDisk(): List<UUID7> = entries
}
