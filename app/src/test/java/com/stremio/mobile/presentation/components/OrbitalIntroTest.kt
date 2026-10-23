package com.stremio.mobile.presentation.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi", application = Application::class)
class OrbitalIntroTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var finished = 0
    private var ready by mutableStateOf(false)

    private fun show(initiallyReady: Boolean) {
        ready = initiallyReady
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            OrbitalIntro(ready = ready, onFinished = { finished++ }, modifier = Modifier.fillMaxSize())
        }
    }

    private fun advance(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    @Test
    fun readyAppLeavesRightAfterTheLogoWithoutExtraHold() {
        show(initiallyReady = true)
        advance(5_000)
        assertEquals("must not cut the animation before the logo is in", 0, finished)
        advance(1_000) // logo settled (5.3 s) + 0.45 s fade
        assertEquals(1, finished)
    }

    @Test
    fun slowStartupHoldsOnTheLogoUntilReady() {
        show(initiallyReady = false)
        advance(9_000)
        assertEquals(0, finished)
        ready = true
        advance(700)
        assertEquals(1, finished)
    }

    @Test
    fun neverCoversTheAppForeverIfStartupHangs() {
        show(initiallyReady = false)
        advance(13_000)
        assertEquals(1, finished)
    }

    @Test
    fun tapSkipsStraightToTheAppWhenReady() {
        show(initiallyReady = true)
        advance(1_000)
        composeRule.onNodeWithContentDescription("MW Play").performClick()
        advance(700)
        assertEquals(1, finished)
    }

    /** Renders key frames to build/orbital-intro/ for a visual check (portrait). */
    @Test
    fun rendersKeyFrames() {
        show(initiallyReady = false)
        val out = File("build/orbital-intro").apply { mkdirs() }
        var at = 0L
        for (ms in longArrayOf(700, 1_800, 2_700, 3_200, 4_300, 5_200, 7_000)) {
            advance(ms - at)
            at = ms
            val view = composeRule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(out, "port_$ms.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(0, finished)
    }
}
