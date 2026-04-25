package com.lap.mobile.capture

/**
 * Recording test double for [PowerLock]. Tracks `acquire` / `release`
 * call counts so [RecordingPowerGuardTest] can assert the lifecycle
 * contract precisely. `internal` so sibling tests in `capture/` can
 * share it without `private class` collisions.
 */
internal class FakePowerLock : PowerLock {
    var acquireCount: Int = 0
        private set
    var releaseCount: Int = 0
        private set
    private var heldFlag: Boolean = false

    override val isHeld: Boolean
        get() = heldFlag

    override fun acquire() {
        heldFlag = true
        acquireCount++
    }

    override fun release() {
        heldFlag = false
        releaseCount++
    }
}
