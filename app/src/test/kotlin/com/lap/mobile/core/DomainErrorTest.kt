package com.lap.mobile.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainErrorTest {
    @Test
    fun `Unknown is a singleton`() {
        assertSame(DomainError.Unknown, DomainError.Unknown)
    }

    @Test
    fun `NotFound round-trips preserving what`() {
        val original: DomainError = DomainError.NotFound(what = "session:abc")
        val payload = lapJson.encodeToString(original)
        assertEquals(original, lapJson.decodeFromString<DomainError>(payload))
    }

    @Test
    fun `Network round-trips preserving status and message`() {
        val original: DomainError = DomainError.Network(status = 502, message = "bad gateway")
        val payload = lapJson.encodeToString(original)
        assertEquals(original, lapJson.decodeFromString<DomainError>(payload))
    }

    @Test
    fun `polymorphic tag uses SerialName`() {
        val err: DomainError = DomainError.Validation(field = "email", message = "must not be blank")
        val payload = lapJson.encodeToString(err)
        assertTrue("expected @type=validation discriminator: $payload", payload.contains("\"@type\":\"validation\""))
    }

    @Test
    fun `when over DomainError is exhaustive`() {
        // Compile-time exhaustiveness guard: if a new variant lands without a branch, this stops compiling.
        val sample: DomainError = DomainError.Unknown
        val label: String = when (sample) {
            is DomainError.Io -> "io"
            is DomainError.Network -> "network"
            is DomainError.Serialization -> "serialization"
            is DomainError.NotFound -> "not_found"
            is DomainError.Unauthorized -> "unauthorized"
            is DomainError.Validation -> "validation"
            DomainError.Unknown -> "unknown"
        }
        assertEquals("unknown", label)
    }
}
