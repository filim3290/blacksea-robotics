package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.screens.BlackseaSplashScreen
import com.example.ui.theme.BlackseaRoboticsTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `splash plays full 3_2s sequence across all 5 scenes before calling onAnimationFinished in light theme`() {
        var finished = false
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BlackseaRoboticsTheme(darkTheme = false) {
                BlackseaSplashScreen(
                    onAnimationFinished = { finished = true }
                )
            }
        }

        // Scene 1 (0-0.5s): bird emerging
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_logo").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("BLACKSEA ROBOTICS Açılış Ekranı").assertIsDisplayed()
        assertFalse("Splash must not finish during Scene 1", finished)

        // Scene 2 (0.5-1.2s): wings open + energy burst
        composeTestRule.mainClock.advanceTimeBy(600)
        assertFalse("Splash must not finish during Scene 2", finished)

        // Scene 3 (1.2-2.4s): BLACK SEA handwriting stroke
        composeTestRule.mainClock.advanceTimeBy(900)
        assertFalse("Splash must not finish during Scene 3", finished)

        // Scene 4 (2.1-2.9s): ROBOTICS staggered reveal
        composeTestRule.mainClock.advanceTimeBy(800)
        assertFalse("Splash must not finish during Scene 4", finished)

        // Scene 5 complete (~3.22s+)
        composeTestRule.mainClock.advanceTimeBy(750)
        composeTestRule.waitForIdle()
        assertTrue("Splash must invoke onAnimationFinished once Scene 5 completes", finished)
    }

    @Test
    fun `renders splash screen properly in dark theme`() {
        var finished = false
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BlackseaRoboticsTheme(darkTheme = true) {
                BlackseaSplashScreen(
                    onAnimationFinished = { finished = true }
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_logo").assertIsDisplayed()
        assertFalse(finished)

        composeTestRule.mainClock.advanceTimeBy(2000)
        composeTestRule.waitForIdle()
        assertTrue(finished)
    }
}
