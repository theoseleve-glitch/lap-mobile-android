package com.lap.mobile.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadPreferencesRepositoryTest {
    @Test
    fun network_policy_defaults_to_unmetered_only_when_no_row_exists() = runTest {
        // Fresh install — no row written yet. Worker must wait for Wi-Fi
        // by default so the user never gets a surprise cellular bill.
        val repo = UploadPreferencesRepository(FakeUploadPreferencesDao())

        assertEquals(NetworkPolicy.UNMETERED_ONLY, repo.networkPolicy())
    }

    @Test
    fun network_policy_is_unmetered_only_when_persisted_row_has_allow_metered_false() = runTest {
        val dao = FakeUploadPreferencesDao().apply {
            row = UploadPreferencesEntity(id = UploadPreferencesEntity.SINGLETON_ID, allowMetered = false)
        }
        val repo = UploadPreferencesRepository(dao)

        assertEquals(NetworkPolicy.UNMETERED_ONLY, repo.networkPolicy())
    }

    @Test
    fun network_policy_is_allow_metered_when_persisted_row_has_allow_metered_true() = runTest {
        val dao = FakeUploadPreferencesDao().apply {
            row = UploadPreferencesEntity(id = UploadPreferencesEntity.SINGLETON_ID, allowMetered = true)
        }
        val repo = UploadPreferencesRepository(dao)

        assertEquals(NetworkPolicy.ALLOW_METERED, repo.networkPolicy())
    }

    @Test
    fun set_allow_metered_true_persists_so_a_restart_observes_the_new_choice() = runTest {
        val dao = FakeUploadPreferencesDao()
        val repo = UploadPreferencesRepository(dao)

        repo.setAllowMetered(allow = true)

        assertEquals(true, dao.row?.allowMetered)
        // The persisted row uses the singleton id so a re-set overwrites
        // the same row — there must never be more than one preferences row.
        assertEquals(UploadPreferencesEntity.SINGLETON_ID, dao.row?.id)
    }

    @Test
    fun set_allow_metered_false_persists_so_the_user_can_opt_back_to_wifi_only() = runTest {
        val dao = FakeUploadPreferencesDao().apply {
            row = UploadPreferencesEntity(id = UploadPreferencesEntity.SINGLETON_ID, allowMetered = true)
        }
        val repo = UploadPreferencesRepository(dao)

        repo.setAllowMetered(allow = false)

        assertEquals(false, dao.row?.allowMetered)
    }

    @Test
    fun toggling_allow_metered_round_trips_through_the_repository() = runTest {
        val repo = UploadPreferencesRepository(FakeUploadPreferencesDao())

        repo.setAllowMetered(allow = true)
        val afterEnable = repo.networkPolicy()
        repo.setAllowMetered(allow = false)
        val afterRevert = repo.networkPolicy()

        assertEquals(NetworkPolicy.ALLOW_METERED, afterEnable)
        assertEquals(NetworkPolicy.UNMETERED_ONLY, afterRevert)
    }

    @Test
    fun battery_policy_defaults_to_defer_when_low_when_no_row_exists() = runTest {
        // Fresh install — no row written yet. Worker must wait for the
        // battery to recover from low so an in-progress multi-hour upload
        // doesn't drain a near-empty device.
        val repo = UploadPreferencesRepository(FakeUploadPreferencesDao())

        assertEquals(BatteryPolicy.DEFER_WHEN_LOW, repo.batteryPolicy())
    }

    @Test
    fun battery_policy_is_defer_when_low_when_persisted_row_has_force_when_battery_low_false() = runTest {
        val dao = FakeUploadPreferencesDao().apply {
            row = UploadPreferencesEntity(
                id = UploadPreferencesEntity.SINGLETON_ID,
                allowMetered = false,
                forceWhenBatteryLow = false,
            )
        }
        val repo = UploadPreferencesRepository(dao)

        assertEquals(BatteryPolicy.DEFER_WHEN_LOW, repo.batteryPolicy())
    }

    @Test
    fun battery_policy_is_force_regardless_when_persisted_row_has_force_when_battery_low_true() = runTest {
        val dao = FakeUploadPreferencesDao().apply {
            row = UploadPreferencesEntity(
                id = UploadPreferencesEntity.SINGLETON_ID,
                allowMetered = false,
                forceWhenBatteryLow = true,
            )
        }
        val repo = UploadPreferencesRepository(dao)

        assertEquals(BatteryPolicy.FORCE_REGARDLESS, repo.batteryPolicy())
    }

    @Test
    fun set_force_when_battery_low_true_persists_so_a_restart_observes_the_user_override() = runTest {
        val dao = FakeUploadPreferencesDao()
        val repo = UploadPreferencesRepository(dao)

        repo.setForceWhenBatteryLow(force = true)

        assertEquals(true, dao.row?.forceWhenBatteryLow)
        assertEquals(UploadPreferencesEntity.SINGLETON_ID, dao.row?.id)
    }

    @Test
    fun setting_force_when_battery_low_does_not_clobber_allow_metered_toggle() = runTest {
        // The two toggles share one singleton row — flipping one MUST
        // preserve the other so the user doesn't have to re-pick their
        // network choice every time they touch battery settings.
        val dao = FakeUploadPreferencesDao()
        val repo = UploadPreferencesRepository(dao)
        repo.setAllowMetered(allow = true)

        repo.setForceWhenBatteryLow(force = true)

        assertEquals(true, dao.row?.allowMetered)
        assertEquals(true, dao.row?.forceWhenBatteryLow)
    }

    @Test
    fun setting_allow_metered_does_not_clobber_force_when_battery_low_toggle() = runTest {
        val dao = FakeUploadPreferencesDao()
        val repo = UploadPreferencesRepository(dao)
        repo.setForceWhenBatteryLow(force = true)

        repo.setAllowMetered(allow = true)

        assertEquals(true, dao.row?.allowMetered)
        assertEquals(true, dao.row?.forceWhenBatteryLow)
    }
}

/**
 * Tiny stand-in for the Room-generated DAO. Holds a single row in
 * memory because the production DAO is a singleton-row table — at most
 * one preferences row per app install.
 */
private class FakeUploadPreferencesDao : UploadPreferencesDao {
    var row: UploadPreferencesEntity? = null

    override suspend fun get(id: String): UploadPreferencesEntity? = row?.takeIf { it.id == id }

    override suspend fun upsert(row: UploadPreferencesEntity) {
        this.row = row
    }
}
