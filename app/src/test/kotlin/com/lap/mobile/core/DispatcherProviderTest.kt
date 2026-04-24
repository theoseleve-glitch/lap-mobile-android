package com.lap.mobile.core

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DispatcherProviderTest {
    // Note: `main` is intentionally NOT tested here. Dispatchers.Main requires
    // the Android main-dispatcher ServiceLoader module, which isn't available
    // in pure JVM unit tests (throws IllegalStateException). Tests for
    // anything that actually dispatches on main must use
    // kotlinx-coroutines-test's Dispatchers.setMain().

    @Test
    fun `default provider exposes Dispatchers IO`() {
        val provider = DefaultDispatcherProvider()
        assertSame(Dispatchers.IO, provider.io)
    }

    @Test
    fun `default provider exposes Dispatchers Default`() {
        val provider = DefaultDispatcherProvider()
        assertSame(Dispatchers.Default, provider.default)
    }

    @Test
    fun `default provider exposes Dispatchers Unconfined`() {
        val provider = DefaultDispatcherProvider()
        assertSame(Dispatchers.Unconfined, provider.unconfined)
    }

    @Test
    fun `distinct dispatcher instances per role`() {
        val provider = DefaultDispatcherProvider()
        val seen = setOf(provider.io, provider.default, provider.unconfined)
        assertEquals("io, default, unconfined must all differ", 3, seen.size)
    }
}
