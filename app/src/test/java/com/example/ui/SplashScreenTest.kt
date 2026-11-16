package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.BuildConfig
import com.example.ui.screens.BlackseaSplashScreen
import com.example.ui.theme.BlackseaRoboticsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplashScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders splash branding and version in light theme`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BlackseaRoboticsTheme(darkTheme = false) {
                BlackseaSplashScreen()
            }
        }
        composeTestRule.mainClock.advanceTimeBy(600)

        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("BLACKSEA ROBOTICS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ünye Fen Lisesi Robotik Takımı").assertIsDisplayed()
        composeTestRule.onNodeWithText("ONE TEAM. ONE VISION. ONE ROBOT.").assertIsDisplayed()
        composeTestRule.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    }

    @Test
    fun `renders splash branding in dark theme`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BlackseaRoboticsTheme(darkTheme = true) {
                BlackseaSplashScreen()
            }
        }
        composeTestRule.mainClock.advanceTimeBy(600)

        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_logo").assertIsDisplayed()
        composeTestRule.onNodeWithText("BLACKSEA ROBOTICS").assertIsDisplayed()
    }
}
