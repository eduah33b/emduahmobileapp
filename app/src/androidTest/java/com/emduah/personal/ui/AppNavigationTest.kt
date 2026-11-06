package com.emduah.personal.ui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.emduah.personal.data.Connection
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Isolated preferences and a fake local session; never touches the owner's session or scheduler. */
class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val restoration = StateRestorationTester(compose)
    private lateinit var application: Application
    private lateinit var vm: PersonalViewModel
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setup() {
        application = object : Application() {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                context.getSharedPreferences("ui-test-$name", Context.MODE_PRIVATE)
        }
        application.getSharedPreferences("personal", Context.MODE_PRIVATE).edit().clear().commit()
        vm = PersonalViewModel(application)
    }
    @After fun cleanup() {
        vm.store.prefs.unregisterOnSharedPreferenceChangeListener(vm)
        vm.store.prefs.edit().clear().commit()
    }
    private fun show(signedIn: Boolean = true, dark: Boolean = true, fontScale: Float = 1f) {
        if (signedIn) vm.store.saveConnection(Connection("https://example.invalid", "ui-test-token", "Test person"))
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        restoration.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(context.resources.displayMetrics.density, fontScale)) {
                PersonalApp(vm) {}
            }
        }
    }
    private fun capture(name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "ui-review-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun firstLaunchRequiresSignIn() {
        show(false)
        compose.onNodeWithText("Welcome home.").assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertIsNotEnabled()
        compose.onNodeWithText("Apps").assertDoesNotExist()
        capture("sign-in")
    }
    @Test fun lightSignInSupportsLargeTextAndConnectionSettings() {
        show(signedIn = false, dark = false, fontScale = 1.5f)
        compose.onNodeWithText("Welcome home.").assertIsDisplayed()
        capture("sign-in-light-large")
        compose.onNodeWithText("Connection settings").performScrollTo().performClick()
        compose.onNodeWithText("Server URL").performScrollTo().assertIsDisplayed()
    }
    @Test fun homeSupportsLargeTextInLightMode() {
        show(dark = false, fontScale = 1.5f)
        compose.onNodeWithText("Open wallpapers").performScrollTo().assertIsDisplayed()
        capture("home-light-large")
        compose.onNodeWithText("Open wallpapers").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Open wallpapers").assertIsDisplayed()
    }
    @Test fun signedInLaunchStartsAtHomeAndFeatureReturnsToApps() {
        show()
        compose.onNodeWithText("Open wallpapers").assertIsDisplayed()
        capture("home")
        compose.onNodeWithText("Apps").performClick()
        capture("apps")
        compose.onNodeWithText("Images and videos for your home and lock screens.").performClick()
        compose.onNodeWithText("A fresh view, at your pace.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your personal tools, in one place.").assertIsDisplayed()
    }
    @Test fun editorAsksBeforeDiscardingAndKeepsDraftOnRecreation() {
        show()
        compose.onNodeWithText("Open wallpapers").performClick()
        compose.onNodeWithText("Configure").performScrollTo().performClick()
        compose.onNodeWithText("Path", substring = false).performScrollTo().performTextInput("draft-folder")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("draft-folder").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("draft-folder").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
        compose.onNodeWithText("A fresh view, at your pace.").assertIsDisplayed()
    }
}
