package com.lap.mobile.ui.navigation

/**
 * Top-level destinations used by the Compose NavHost. String paths are
 * the currency Compose Navigation 2.x speaks; this sealed class just
 * gives them a typed handle so screens can reference `Routes.Dashboard`
 * instead of `"dashboard"` at every call site.
 *
 * Parameterised routes (e.g. `dashboard/{sessionId}`) will arrive as
 * later tasks need them — this skeleton only models the flat
 * destinations the sprint names explicitly (CLAUDE.md + T024).
 */
sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Login : Routes("login")
    data object SessionList : Routes("session_list")
    data object Dashboard : Routes("dashboard")
    data object Preflight : Routes("preflight")
    data object Uploads : Routes("uploads")
    data object Settings : Routes("settings")

    companion object {
        /** The NavHost start destination. */
        const val START: String = "home"
    }
}
