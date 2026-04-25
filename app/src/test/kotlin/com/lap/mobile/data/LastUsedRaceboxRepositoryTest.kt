package com.lap.mobile.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val FIXED_NOW: Instant = Instant.parse("2026-04-25T14:30:12Z")
private const val ADDRESS_A: String = "AA:BB:CC:DD:EE:01"
private const val NAME_A: String = "RaceBox Mini #1"
private const val ADDRESS_B: String = "AA:BB:CC:DD:EE:02"
private const val NAME_B: String = "RaceBox Mini #2"

class LastUsedRaceboxRepositoryTest {
    @Test
    fun saveSuccessfulPairing_writes_entity_with_singleton_id() = runTest {
        val dao = FakeLastUsedRaceboxDao()
        val repo = LastUsedRaceboxRepository(dao = dao, now = { FIXED_NOW })

        repo.saveSuccessfulPairing(address = ADDRESS_A, name = NAME_A)

        val stored = requireNotNull(dao.row)
        assertEquals(LastUsedRaceboxEntity.SINGLETON_ID, stored.id)
        assertEquals(ADDRESS_A, stored.address)
        assertEquals(NAME_A, stored.name)
        assertEquals(FIXED_NOW, stored.savedAt)
    }

    @Test
    fun saveSuccessfulPairing_overwrites_previous_singleton() = runTest {
        val dao = FakeLastUsedRaceboxDao()
        val repo = LastUsedRaceboxRepository(dao = dao, now = { FIXED_NOW })
        repo.saveSuccessfulPairing(address = ADDRESS_A, name = NAME_A)

        repo.saveSuccessfulPairing(address = ADDRESS_B, name = NAME_B)

        assertEquals(ADDRESS_B, dao.row?.address)
        assertEquals(NAME_B, dao.row?.name)
    }

    @Test
    fun get_returns_null_when_dao_empty() = runTest {
        val repo = LastUsedRaceboxRepository(FakeLastUsedRaceboxDao(), now = { FIXED_NOW })

        assertNull(repo.get())
    }

    @Test
    fun get_maps_entity_to_domain_type() = runTest {
        val dao = FakeLastUsedRaceboxDao()
        val repo = LastUsedRaceboxRepository(dao = dao, now = { FIXED_NOW })
        repo.saveSuccessfulPairing(address = ADDRESS_A, name = NAME_A)

        val recalled = requireNotNull(repo.get())

        assertEquals(ADDRESS_A, recalled.address)
        assertEquals(NAME_A, recalled.name)
        assertEquals(FIXED_NOW, recalled.savedAt)
    }

    @Test
    fun saveSuccessfulPairing_accepts_null_name() = runTest {
        val dao = FakeLastUsedRaceboxDao()
        val repo = LastUsedRaceboxRepository(dao = dao, now = { FIXED_NOW })

        repo.saveSuccessfulPairing(address = ADDRESS_A, name = null)

        assertNull(dao.row?.name)
        assertEquals(ADDRESS_A, dao.row?.address)
    }

    @Test
    fun clear_empties_the_dao() = runTest {
        val dao = FakeLastUsedRaceboxDao()
        val repo = LastUsedRaceboxRepository(dao = dao, now = { FIXED_NOW })
        repo.saveSuccessfulPairing(address = ADDRESS_A, name = NAME_A)

        repo.clear()

        assertNull(dao.row)
        assertTrue(dao.cleared)
    }
}

private class FakeLastUsedRaceboxDao : LastUsedRaceboxDao {
    var row: LastUsedRaceboxEntity? = null
    var cleared: Boolean = false

    override suspend fun upsert(row: LastUsedRaceboxEntity) {
        this.row = row
    }

    override suspend fun get(): LastUsedRaceboxEntity? = row

    override suspend fun clear() {
        row = null
        cleared = true
    }
}
