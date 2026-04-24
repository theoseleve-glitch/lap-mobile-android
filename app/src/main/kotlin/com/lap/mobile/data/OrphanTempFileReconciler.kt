package com.lap.mobile.data

import com.lap.schemas.v1.UUID7

/**
 * Startup reconciler paired with [JournalReconciler]: finds buffer
 * files on disk whose session row is gone from the DB. Those files are
 * leaked bytes — most often from a crash between buffer-write and
 * SessionEntity-insert, or from a user-initiated delete that left the
 * file behind (T162).
 *
 * Direction only: this reconciler reports disk files WITHOUT a DB
 * row. The reverse case (DB row without a buffer file) is a different
 * problem and is T057's concern — a session that entered
 * `recording`/`finalizing` but never finished could legitimately have
 * its buffer file deleted mid-flight.
 *
 * Deletion happens elsewhere — the reconciler only identifies orphans
 * so the UI (T162 abandon flow) or cleanup job can decide policy.
 */
class OrphanTempFileReconciler(
    private val inventory: TempFileInventory,
    private val sessionDao: SessionDao,
) {
    suspend fun findOrphans(): List<UUID7> {
        val onDisk = inventory.listSessionIdsOnDisk()
        if (onDisk.isEmpty()) return emptyList()
        return onDisk.filter { sessionDao.findById(it) == null }
    }
}
