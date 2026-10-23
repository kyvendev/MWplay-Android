package com.stremio.mobile.presentation.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = Application::class)
class OrbitalIntroTvTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun key(code: Int, action: Int) = KeyEvent(AndroidKeyEvent(action, code))

    @Test
    fun remoteKeysAreSwallowedAndOkSkips() {
        var skips = 0
        val dpad = listOf(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        for (code in dpad) {
            assertTrue(interceptIntroKey(key(code, AndroidKeyEvent.ACTION_DOWN)) { skips++ })
            assertTrue(interceptIntroKey(key(code, AndroidKeyEvent.ACTION_UP)) { skips++ })
        }
        assertEquals("arrows never skip", 0, skips)

        assertTrue(interceptIntroKey(key(AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.ACTION_DOWN)) { skips++ })
        assertTrue(interceptIntroKey(key(AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.ACTION_UP)) { skips++ })
        assertEquals("OK skips once (on release)", 1, skips)

        assertFalse("Back keeps its normal behaviour", interceptIntroKey(key(AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.ACTION_UP)) { skips++ })
    }

    @Test
    fun dpadDoesNotMoveFocusUnderTheIntroAndOkLeavesWhenReady() {
        var visible = true
        var finished = 0
        var skips by mutableIntStateOf(0)
        val first = FocusRequester()
        // Manual clock: otherwise idling runs the whole animation before the first key press.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(Modifier.fillMaxSize().onPreviewKeyEvent { visible && interceptIntroKey(it) { skips++ } }) {
                Row {
                    Box(Modifier.size(80.dp).testTag("first").focusRequester(first).focusable())
                    Box(Modifier.size(80.dp).testTag("second").focusable())
                }
                OrbitalIntro(ready = true, onFinished = { finished++; visible = false }, modifier = Modifier.fillMaxSize(), skipRequests = skips)
            }
        }
        composeRule.runOnIdle { first.requestFocus() }
        composeRule.onNodeWithTag("first").assertIsFocused()

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("first").assertIsFocused()
        assertEquals("still covering the app", 0, finished)

        composeRule.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(1, finished)
        // After the intro the home content still owns focus and the remote works again.
        composeRule.onNodeWithTag("first").assertIsFocused()
        composeRule.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("second").assertIsFocused()
    }
}

/** Landscape TV frames written to build/orbital-intro/ for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = Application::class)
class OrbitalIntroTvFramesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersKeyFrames() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { OrbitalIntro(ready = false, onFinished = {}, modifier = Modifier.fillMaxSize()) }
        val out = File("build/orbital-intro").apply { mkdirs() }
        var at = 0L
        for (ms in longArrayOf(1_800, 3_200, 5_200)) {
            composeRule.mainClock.advanceTimeBy(ms - at)
            composeRule.waitForIdle()
            at = ms
            val view = composeRule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(out, "tv_$ms.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
