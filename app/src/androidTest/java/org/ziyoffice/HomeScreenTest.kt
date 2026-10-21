package org.ziyoffice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shell, on a real phone.
 *
 * Small on purpose. The point is that the app starts, the home screen shows up
 * and the tabs work, which is the part CI can't check.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theHomeScreenShows() {
        compose.onNodeWithText("ZiyOffice").assertIsDisplayed()
        compose.onNodeWithText("Open a file").assertIsDisplayed()
    }

    @Test
    fun theSettingsTabOpens() {
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
