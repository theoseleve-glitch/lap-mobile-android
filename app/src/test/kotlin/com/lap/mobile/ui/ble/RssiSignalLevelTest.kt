package com.lap.mobile.ui.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class RssiSignalLevelTest {
    @Test
    fun excellent_for_strong_signals_above_minus60() {
        assertEquals(RssiSignalLevel.EXCELLENT, RssiSignalLevel.fromDbm(-30))
        assertEquals(RssiSignalLevel.EXCELLENT, RssiSignalLevel.fromDbm(-55))
    }

    @Test
    fun excellent_at_exactly_minus60() {
        assertEquals(RssiSignalLevel.EXCELLENT, RssiSignalLevel.fromDbm(-60))
    }

    @Test
    fun good_in_minus61_to_minus75_range() {
        assertEquals(RssiSignalLevel.GOOD, RssiSignalLevel.fromDbm(-61))
        assertEquals(RssiSignalLevel.GOOD, RssiSignalLevel.fromDbm(-70))
        assertEquals(RssiSignalLevel.GOOD, RssiSignalLevel.fromDbm(-75))
    }

    @Test
    fun fair_in_minus76_to_minus90_range() {
        assertEquals(RssiSignalLevel.FAIR, RssiSignalLevel.fromDbm(-76))
        assertEquals(RssiSignalLevel.FAIR, RssiSignalLevel.fromDbm(-85))
        assertEquals(RssiSignalLevel.FAIR, RssiSignalLevel.fromDbm(-90))
    }

    @Test
    fun poor_below_minus90() {
        assertEquals(RssiSignalLevel.POOR, RssiSignalLevel.fromDbm(-91))
        assertEquals(RssiSignalLevel.POOR, RssiSignalLevel.fromDbm(-110))
    }
}
