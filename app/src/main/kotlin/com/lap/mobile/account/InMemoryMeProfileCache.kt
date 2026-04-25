package com.lap.mobile.account

import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1 process-local [MeProfileCache] (T189).
 *
 * Holds the most-recently-fetched [MeProfile] in a single nullable
 * field. Process death drops the entry; cold launch with a cached
 * refresh token will re-populate via [SyncMeProfileUseCase] on the
 * next auth-restore kick (T166).
 *
 * `@Synchronized` because the orchestrator may write from a
 * coroutine on Dispatchers.IO while UI consumers read on the main
 * thread. The contention is minimal (write happens at most once per
 * login / session resume; reads are pull-style) so the synchronization
 * cost is irrelevant. A `@Volatile` field would also work but
 * `@Synchronized` keeps `clear` atomic against a concurrent `write`.
 */
@Singleton
class InMemoryMeProfileCache
@Inject
constructor() : MeProfileCache {
    private var profile: MeProfile? = null

    @Synchronized
    override fun read(): MeProfile? = profile

    @Synchronized
    override fun write(profile: MeProfile) {
        this.profile = profile
    }

    @Synchronized
    override fun clear() {
        profile = null
    }
}
