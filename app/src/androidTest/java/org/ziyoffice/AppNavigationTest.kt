package org.ziyoffice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeDashboardRendersExpectedActions() {
        compose.onNodeWithText("ZiyOffice").assertIsDisplayed()
        compose.onNodeWithText("Open a file").assertIsDisplayed()
        compose.onNodeWithText("Docs").assertIsDisplayed()
        compose.onNodeWithText("Sheets").assertIsDisplayed()
        compose.onNodeWithText("Slides").assertIsDisplayed()
        compose.onNodeWithText("PDF").assertIsDisplayed()
    }

    @Test
    fun filesTabDisplaysCategoryFilters() {
        compose.onNodeWithText("Files").performClick()
        compose.onNodeWithText("Recent files").assertIsDisplayed()
        compose.onNodeWithText("Browse device").assertIsDisplayed()
        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNodeWithText("Starred").assertIsDisplayed()
        compose.onNodeWithText("Docs").assertIsDisplayed()
    }

    @Test
    fun settingsTabOpensAboutScreen() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("License").assertIsDisplayed()
        compose.onNodeWithText("GNU GPL-3.0-or-later, free software forever. The full source text ships in the project repository.").assertIsDisplayed()
    }
}
