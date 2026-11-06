package com.emduah.personal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.emduah.personal.data.Profile
import com.emduah.personal.data.Screen
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WallpaperEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun savesBasicAndAdvancedSettingsTogether() {
        var saved: Profile? = null
        compose.setContent {
            EmduahTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    ProfileEditor(Profile(Screen.HOME), JSONObject(), {}, { saved = it }, {}, {})
                }
            }
        }
        compose.onNodeWithText("Images", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Videos", substring = false).performClick()
        compose.onNodeWithText("Any rating").performScrollTo().performClick()
        compose.onNodeWithText("3 stars & above").performClick()
        compose.onNodeWithText("Path", substring = false).performScrollTo().performTextInput("forest -draft")
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Advanced filters", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Directory (includes subfolders)").performScrollTo().performTextInput("comfy/output")
        compose.onNodeWithText("Additional filter JSON").performScrollTo().performTextReplacement("""{"model":"chosen"}""")
        compose.onNodeWithText("Change every (minutes)").performScrollTo().performTextReplacement("45")
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved) { "Save must deliver a validated profile" }
            assertTrue(result.motion)
            assertFalse(result.wifiOnly)
            assertEquals(45L, result.minutes)
            val filters = result.requestFilters()
            assertEquals("video", filters.getString("type"))
            assertEquals("[3,4,5]", filters.getJSONArray("rating").toString())
            assertEquals("forest -draft", filters.getString("path"))
            assertEquals("comfy/output", filters.getString("directory"))
            assertEquals("chosen", filters.getString("model"))
        }
    }
    @Test fun cancelDoesNotSaveEditedFilters() {
        var saved = false
        var cancelled = false
        compose.setContent {
            EmduahTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    ProfileEditor(Profile(Screen.LOCK), JSONObject(), {}, { saved = true }, { cancelled = true }, {})
                }
            }
        }
        compose.onNodeWithText("Path", substring = false).performScrollTo().performTextInput("unsaved")
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled); assertFalse(saved) }
    }
}
