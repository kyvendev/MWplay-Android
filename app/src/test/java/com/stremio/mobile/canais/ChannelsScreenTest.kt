package com.stremio.mobile.canais

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.core.theme.StremioBackground
import com.stremio.mobile.data.model.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The phone home of MW Play Canais, with touch. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi", application = Application::class)
class ChannelsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val played = mutableListOf<Pair<CatalogItem, List<CatalogItem>>>()
    private val selected = mutableListOf<ResourceRequest>()
    private var favorites by mutableStateOf(emptyList<CatalogItem>())

    private fun show() = composeRule.setContent {
        Box(Modifier.fillMaxSize().background(StremioBackground)) {
            ChannelsScreen(
                filters = ChannelsFixtures.filters(),
                catalog = ChannelsFixtures.shelf(),
                favorites = favorites,
                onLoadChannels = {},
                onSelectRequest = { selected += it },
                onPlay = { item, queue -> played += item to queue },
                onToggleFavorite = { item ->
                    favorites = if (favorites.any { it.id == item.id }) favorites.filterNot { it.id == item.id } else favorites + item
                    favorites.any { it.id == item.id }
                },
                onOpenSettings = {},
            )
        }
    }

    @Test
    fun aTapPlaysTheChannelWithTheListForZapping() {
        show()
        composeRule.onNodeWithText("Canais").assertExists()
        composeRule.onNodeWithText("SporTV").performClick()
        composeRule.runOnIdle {
            assertEquals(ChannelsFixtures.sportv, played.single().first)
            assertEquals(ChannelsFixtures.channels, played.single().second)
        }
    }

    @Test
    fun categoriesLoadTheSourceCatalogs() {
        show()
        composeRule.onNodeWithText("Esportes").performClick()
        composeRule.onNodeWithText("Canais | Notícias").performClick()
        composeRule.runOnIdle { assertEquals(listOf(ChannelsFixtures.sports, ChannelsFixtures.news), selected) }
    }

    @Test
    fun aLongPressAddsToFavoritesWhichHaveTheirOwnCategory() {
        show()
        composeRule.onNodeWithText("Favoritos").performClick()
        composeRule.onNodeWithText("Você ainda não tem favoritos", substring = true).assertExists()
        composeRule.onNodeWithText("Todos os canais").performClick()
        composeRule.onNodeWithText("CNN Brasil").performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(listOf(ChannelsFixtures.cnn), favorites) }
        composeRule.onNodeWithText("Favoritos").performClick()
        composeRule.onNodeWithText("CNN Brasil").assertExists()
        composeRule.onNodeWithText("Globo").assertDoesNotExist()
    }

    @Test
    fun searchFiltersByName() {
        show()
        composeRule.onNodeWithContentDescription("Buscar canal").performClick()
        composeRule.onNodeWithTag("channel-search").performTextInput("espn")
        composeRule.onNodeWithText("ESPN").assertExists()
        composeRule.onNodeWithText("Globo").assertDoesNotExist()
        shot("search")
    }

    @Test
    fun rendersTheHome() {
        favorites = listOf(ChannelsFixtures.sportv)
        show()
        shot("home")
    }

    private fun shot(name: String) {
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val out = File("build/canais-shot").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
