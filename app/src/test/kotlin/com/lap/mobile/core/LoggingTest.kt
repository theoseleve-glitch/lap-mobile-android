package com.lap.mobile.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import timber.log.Timber

class LoggingTest {
    @Test
    fun `debug build plants Timber DebugTree`() {
        val tree = createLoggingTree(debug = true)
        assertTrue("expected Timber.DebugTree for debug=true, got ${tree::class}", tree is Timber.DebugTree)
    }

    @Test
    fun `release build does NOT plant a DebugTree`() {
        val tree = createLoggingTree(debug = false)
        assertFalse("expected non-DebugTree for debug=false, got ${tree::class}", tree is Timber.DebugTree)
    }

    @Test
    fun `release tree skips verbose info debug priorities`() {
        val tree = createLoggingTree(debug = false)
        assertFalse(tree.isLoggableForTest(priority = 2)) // VERBOSE
        assertFalse(tree.isLoggableForTest(priority = 3)) // DEBUG
        assertFalse(tree.isLoggableForTest(priority = 4)) // INFO
        assertTrue(tree.isLoggableForTest(priority = 5)) // WARN
        assertTrue(tree.isLoggableForTest(priority = 6)) // ERROR
    }

    private fun Timber.Tree.isLoggableForTest(priority: Int): Boolean =
        this.javaClass.getDeclaredMethod("isLoggable", String::class.java, Int::class.javaPrimitiveType).let {
            it.isAccessible = true
            it.invoke(this, null, priority) as Boolean
        }
}
