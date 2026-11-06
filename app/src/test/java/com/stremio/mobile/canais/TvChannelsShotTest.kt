package com.stremio.mobile.canais

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.stremio.mobile.core.theme.StremioBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the TV home to build/canais-shot (visual check; focus does not land in this mode). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = Application::class)
class TvChannelsShotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersTheTvHome() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(StremioBackground)) {
                ChannelsScreen(
                    filters = ChannelsFixtures.filters(),
                    catalog = ChannelsFixtures.shelf(),
                    favorites = listOf(ChannelsFixtures.sportv),
                    onLoadChannels = {},
                    onSelectRequest = {},
                    onPlay = { _, _ -> },
                    onToggleFavorite = { false },
                    onOpenSettings = {},
                    onCloseApp = {},
                )
            }
        }
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val out = File("build/canais-shot").apply { mkdirs() }
        File(out, "tv-home.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
