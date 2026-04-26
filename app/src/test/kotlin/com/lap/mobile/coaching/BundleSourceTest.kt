package com.lap.mobile.coaching

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the [BundleSource.shouldShowStaleBanner] UX-policy contract
 * (T211 — port of the desktop Python `BundleSource.should_show_stale_banner`
 * property).
 *
 * Why each variant must NOT trigger the banner except STALE_CACHE:
 *   - FRESH_SERVER: the server just gave us up-to-the-second
 *     coaching rules. Showing "rules out of date" would be a UX
 *     lie that erodes trust in every other warning the app shows.
 *   - FRESH_CACHE: cache is within TTL — coaching is current
 *     enough that the missing-N-minutes risk doesn't justify
 *     interrupting the user.
 *   - STALE_CACHE: server is unreachable AND cache is over TTL.
 *     Coaching MAY miss recently-shipped rules. Worth a passive
 *     banner (not a blocking dialog) so user knows to expect
 *     possibly-outdated tips.
 *   - NO_BUNDLE: no cache + no server. Coaching simply isn't
 *     running. Shows its own empty-state UX (a placeholder card
 *     saying "Coaching unavailable, sign in or check connection")
 *     — the banner would be redundant and contradict the empty
 *     state.
 *
 * Locking each variant's behavior individually catches a future
 * refactor that "fixes" the banner to fire on FRESH_CACHE too
 * (premature pessimism) or that drops the banner from STALE_CACHE
 * (silent degradation).
 */
class BundleSourceTest {
    @Test
    fun fresh_server_does_not_show_stale_banner_so_just_fetched_rules_dont_misleadingly_warn() {
        assertFalse(BundleSource.FRESH_SERVER.shouldShowStaleBanner)
    }

    @Test
    fun fresh_cache_does_not_show_stale_banner_so_in_ttl_coaching_doesnt_interrupt_user() {
        assertFalse(BundleSource.FRESH_CACHE.shouldShowStaleBanner)
    }

    @Test
    fun stale_cache_shows_banner_so_user_knows_coaching_may_miss_recently_shipped_rules() {
        // Headline T211 invariant: STALE_CACHE is the ONE state
        // that shows the banner. If a future refactor breaks
        // this, users coaching on out-of-date rules get no
        // warning — silent degradation.
        assertTrue(BundleSource.STALE_CACHE.shouldShowStaleBanner)
    }

    @Test
    fun no_bundle_does_not_show_stale_banner_so_empty_state_ux_isnt_contradicted() {
        // NO_BUNDLE has its own empty-state card ("Coaching
        // unavailable, sign in or check connection"). A banner
        // on top of that would redundantly warn about a state
        // the user already sees explicitly.
        assertFalse(BundleSource.NO_BUNDLE.shouldShowStaleBanner)
    }
}
