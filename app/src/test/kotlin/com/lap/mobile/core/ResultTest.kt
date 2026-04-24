package com.lap.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultTest {
    @Test
    fun `Ok holds its value`() {
        val result: Result<String, DomainError> = Result.Ok("hello")
        assertTrue(result is Result.Ok)
        assertEquals("hello", (result as Result.Ok).value)
    }

    @Test
    fun `Err holds its error`() {
        val result: Result<String, DomainError> = Result.Err(DomainError.Unknown)
        assertTrue(result is Result.Err)
        assertEquals(DomainError.Unknown, (result as Result.Err).error)
    }

    @Test
    fun `when over Result is exhaustive`() {
        val ok: Result<String, DomainError> = Result.Ok("x")
        val err: Result<String, DomainError> = Result.Err(DomainError.Unknown)

        val okLabel = when (ok) {
            is Result.Ok -> "ok:${ok.value}"
            is Result.Err -> "err:${ok.error}"
        }
        val errLabel = when (err) {
            is Result.Ok -> "ok:${err.value}"
            is Result.Err -> "err:${err.error}"
        }

        assertEquals("ok:x", okLabel)
        assertEquals("err:${DomainError.Unknown}", errLabel)
    }

    @Test
    fun `Ok and Err with same inner payload are not equal`() {
        // Belt-and-suspenders: Ok("x") and Err("x") should not accidentally compare equal.
        val ok: Result<String, String> = Result.Ok("x")
        val err: Result<String, String> = Result.Err("x")
        assertTrue(ok != err)
    }
}
