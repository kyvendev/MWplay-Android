package com.stremio.mobile.canais

import android.app.Application
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.stremio.mobile.core.theme.StremioBackground
import com.stremio.mobile.data.model.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MW Play Canais on TV, with the remote only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class TvChannelsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val played = mutableListOf<CatalogItem>()
    private var favorites by mutableStateOf(emptyList<CatalogItem>())
    private var closes = 0

    private fun show() = composeRule.setContent {
        Box(Modifier.fillMaxSize().background(StremioBackground)) {
            ChannelsScreen(
                filters = ChannelsFixtures.filters(),
                catalog = ChannelsFixtures.shelf(),
                favorites = favorites,
                onLoadChannels = {},
                onSelectRequest = {},
                onPlay = { item, _ -> played += item },
                onToggleFavorite = { item ->
                    favorites = if (favorites.any { it.id == item.id }) favorites.filterNot { it.id == item.id } else favorites + item
                    favorites.any { it.id == item.id }
                },
                onOpenSettings = {},
                onCloseApp = { closes++ },
            )
        }
    }

    @Test
    fun remoteStartsOnTheFirstChannelMovesAndPlays() {
        show()
        composeRule.onNodeWithText("Globo").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("SporTV").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(listOf(ChannelsFixtures.sportv), played) }
    }

    @Test
    fun holdingOkAddsToFavoritesWithoutPlaying() {
        show()
        composeRule.onNodeWithText("Globo").assertIsFocused()
        // OK held: the first down, then the key repeat that marks a long press, then the release.
        val view = composeRule.activity.window.decorView
        composeRule.runOnIdle {
            view.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER, 0))
            view.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER, 1))
            view.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER, 0))
        }
        composeRule.runOnIdle {
            assertEquals(listOf(ChannelsFixtures.globo), favorites)
            assertTrue("a long press never plays", played.isEmpty())
        }
    }

    @Test
    fun closeAppIsInTheHeaderAndReachable() {
        show()
        composeRule.onNodeWithContentDescription("Fechar app").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithContentDescription("Fechar app").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(1, closes) }
    }
}
