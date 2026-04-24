package com.lap.mobile

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndLandsOnStartDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appName = context.getString(R.string.app_name)
        composeRule.onNodeWithText(appName).assertExists()
    }
}
