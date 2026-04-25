package com.lap.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the migration policy so the Room builder wiring (applied at
 * app startup, Android-runtime) stays honest. The policy itself is a
 * pure JVM data structure; asserting it in unit tests is cheap, while
 * the actual Room builder can't run without an Android context.
 */
class MigrationPolicyTest {
    @Test
    fun dev_policy_enables_destructive_fallback() {
        val policy = migrationPolicyFor(destructiveMigrationAllowed = true)
        assertTrue(
            "dev flavor must allow destructive fallback to unblock rapid schema iteration",
            policy.destructiveFallback,
        )
    }

    @Test
    fun prod_policy_disables_destructive_fallback() {
        val policy = migrationPolicyFor(destructiveMigrationAllowed = false)
        assertFalse(
            "prod must NEVER wipe user data; strict migrations only",
            policy.destructiveFallback,
        )
    }

    @Test
    fun policy_includes_every_migration_from_v1_to_schema_head() {
        val policy = migrationPolicyFor(destructiveMigrationAllowed = false)
        val ranges = policy.migrations.map { it.startVersion to it.endVersion }.toSet()
        assertEquals(
            "migrations must walk every version step up to SCHEMA_VERSION",
            setOf(
                1 to 2,
                2 to 3,
                3 to 4,
                4 to 5,
                5 to 6,
                6 to 7,
                7 to 8,
                8 to 9,
                9 to 10,
            ),
            ranges,
        )
    }

    @Test
    fun migration_chain_terminates_at_current_schema_version() {
        val policy = migrationPolicyFor(destructiveMigrationAllowed = false)
        val maxEnd = policy.migrations.maxOf { it.endVersion }
        assertEquals(
            "last migration must end at the current SCHEMA_VERSION so the chain is complete",
            LapDatabase.SCHEMA_VERSION,
            maxEnd,
        )
    }
}
