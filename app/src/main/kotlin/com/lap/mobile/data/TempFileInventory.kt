package com.lap.mobile.data

import com.lap.schemas.v1.UUID7

/**
 * Abstracts the on-disk temp-file directory so the reconciler
 * ([OrphanTempFileReconciler]) can be unit-tested without a real
 * filesystem. The production implementation scans the
 * CaptureForegroundService's buffer directory (T079) and returns one
 * session UUID per `.bundle` / `.buffer` file found.
 *
 * Keeping this an interface also insulates the reconciler from the
 * file-naming convention — the mapping from filename to session id
 * lives in the inventory.
 */
interface TempFileInventory {
    suspend fun listSessionIdsOnDisk(): List<UUID7>
}
