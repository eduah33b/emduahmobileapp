package com.emduah.personal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LockViewSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun enablesValidatesSavesAndDisablesMinimumViews() {
        var minimum by mutableStateOf(0)
        compose.setContent { EmduahTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LockViewSettings(minimum, 0, true) { minimum = it }
            }
        } }
        compose.onNode(isToggleable()).performClick()
        compose.runOnIdle { assertEquals(3, minimum) }
        compose.onNodeWithText("Minimum lock-screen views").performTextReplacement("0")
        compose.onNodeWithText("Save minimum views").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Minimum lock-screen views").performScrollTo().performTextReplacement("5")
        compose.onNodeWithText("Save minimum views").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(5, minimum) }
        compose.onNodeWithText("Viewed 0 of 5 times", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, minimum) }
    }
}
