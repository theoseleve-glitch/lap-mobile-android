package com.lap.mobile.ui.ble

import com.lap.mobile.capture.ble.BleConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class RaceboxStatusDisplayTest {
    @Test
    fun disconnected_renders_as_neutral() {
        assertEquals(
            RaceboxStatusVariant.NEUTRAL,
            RaceboxStatusDisplay.variantFor(BleConnectionState.DISCONNECTED),
        )
    }

    @Test
    fun scanning_renders_as_active() {
        assertEquals(
            RaceboxStatusVariant.ACTIVE,
            RaceboxStatusDisplay.variantFor(BleConnectionState.SCANNING),
        )
    }

    @Test
    fun connecting_renders_as_active() {
        assertEquals(
            RaceboxStatusVariant.ACTIVE,
            RaceboxStatusDisplay.variantFor(BleConnectionState.CONNECTING),
        )
    }

    @Test
    fun connected_renders_as_success() {
        assertEquals(
            RaceboxStatusVariant.SUCCESS,
            RaceboxStatusDisplay.variantFor(BleConnectionState.CONNECTED),
        )
    }

    @Test
    fun lost_renders_as_warning() {
        assertEquals(
            RaceboxStatusVariant.WARNING,
            RaceboxStatusDisplay.variantFor(BleConnectionState.LOST),
        )
    }
}
