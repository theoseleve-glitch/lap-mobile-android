package com.lap.mobile.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    private val all: List<Routes> = listOf(
        Routes.Home,
        Routes.Login,
        Routes.SessionList,
        Routes.Dashboard,
        Routes.Preflight,
        Routes.Uploads,
        Routes.Settings,
    )

    @Test
    fun `every route exposes a non-blank path`() {
        all.forEach { r ->
            assertFalse("blank route for ${r::class.simpleName}", r.route.isBlank())
        }
    }

    @Test
    fun `route paths are unique`() {
        val paths = all.map(Routes::route)
        assertEquals("duplicate route paths: $paths", paths.size, paths.toSet().size)
    }

    @Test
    fun `route paths match snake_case convention`() {
        val pattern = Regex("^[a-z][a-z0-9_]*$")
        all.forEach { r ->
            assertTrue(
                "route '${r.route}' for ${r::class.simpleName} breaks snake_case",
                pattern.matches(r.route),
            )
        }
    }

    @Test
    fun `Home is the starting destination`() {
        assertEquals("home", Routes.Home.route)
        assertEquals(Routes.Home.route, Routes.START)
    }
}
