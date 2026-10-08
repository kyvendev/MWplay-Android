package com.stremio.mobile.presentation.components

import android.app.Application
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "television", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class ContinueWatchingShelfTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val movie = CatalogItem("movie", "movie", "Movie", null, null, null, null, progress = 0.4f, isContinueWatching = true)
    private val channel = CatalogItem("channel", "tv", "Live channel", null, null, null, null, progress = 0.2f, isContinueWatching = true)
    private val series = CatalogItem("series", "series", "Series", null, null, null, null, continueWatchingVideoId = "episode", isContinueWatching = true)

    @Test
    fun shortOkAndEnterKeepExistingSingleClickBehavior() {
        var clicks = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalGlobalUiTheme provides GlobalUiTheme(style = "modern", hapticsEnabled = false)) {
                ContinueWatchingShelf(CatalogShelf("Continue Watching", listOf(movie), false), { clicks++ }, {})
            }
        }

        poster("Movie").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionCenter) }
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.runOnIdle { assertEquals(2, clicks) }
        composeRule.onNodeWithText("Remover de continuar assistindo").assertDoesNotExist()
    }

    @Test
    fun heldOkOpensMenuAndItsRepeatsAndReleaseCannotResumeVideo() {
        var clicks = 0
        var timeoutMillis = 0L
        composeRule.setContent {
            timeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
            CompositionLocalProvider(LocalGlobalUiTheme provides GlobalUiTheme(style = "modern", hapticsEnabled = false)) {
                ContinueWatchingShelf(CatalogShelf("Continue Watching", listOf(movie), false), { clicks++ }, {})
            }
        }

        poster("Movie").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { keyDown(Key.DirectionCenter) }
        composeRule.mainClock.advanceTimeBy(timeoutMillis + 100)
        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused()

        // Route the still-held physical key to the newly focused Android dialog window.
        dispatchToDialog(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER, repeat = 1)
        dispatchToDialog(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.runOnIdle { assertEquals(0, clicks) }
        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused()

        dispatchToDialog(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        dispatchToDialog(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.runOnIdle { assertEquals(1, clicks) }
        composeRule.onNodeWithText("Remover de continuar assistindo").assertDoesNotExist()
    }

    @Test
    fun heldEnterOpensMenuWithoutPerformingShortClick() {
        var clicks = 0
        var timeoutMillis = 0L
        composeRule.setContent {
            timeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
            ContinueWatchingShelf(CatalogShelf("Continue Watching", listOf(channel), false), { clicks++ }, {})
        }

        poster("Live channel").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { keyDown(Key.Enter) }
        composeRule.mainClock.advanceTimeBy(timeoutMillis + 100)
        dispatchToDialog(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_ENTER)

        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun touchLongPressAndRemoteCancelRestorePosterFocus() {
        var clicks = 0
        composeRule.setContent {
            ContinueWatchingShelf(CatalogShelf("Continue Watching", listOf(movie), false), { clicks++ }, {})
        }

        poster("Movie").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }

        poster("Movie").assertIsFocused()
        composeRule.onNodeWithText("Remover de continuar assistindo").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun remoteBackDismissesMenuAndRestoresPosterWithoutRemovingIt() {
        var removals = 0
        composeRule.setContent {
            ContinueWatchingShelf(CatalogShelf("Continue Watching", listOf(movie), false), {}, { removals++ })
        }
        poster("Movie").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused()
        dispatchToDialog(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BACK)
        dispatchToDialog(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_BACK)

        composeRule.onNodeWithText("Remover de continuar assistindo").assertDoesNotExist()
        poster("Movie").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, removals) }
    }

    @Test
    fun remoteRemovalRestoresNeighborAndWorksForLiveChannels() {
        var items by mutableStateOf(listOf(movie, channel, series))
        var removed: CatalogItem? = null
        var clicks = 0
        composeRule.setContent {
            ContinueWatchingShelf(
                CatalogShelf("Continue Watching", items, false),
                { clicks++ },
                { item -> removed = item; items = items.filterNot { it.id == item.id && it.type == item.type } },
            )
        }

        poster("Live channel").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }

        poster("Live channel").assertDoesNotExist()
        poster("Series").assertIsFocused()
        composeRule.runOnIdle { assertEquals(channel, removed); assertEquals(0, clicks) }
    }

    @Test
    fun detailsActionClearsAutomaticResumeHintsWithoutRemovingItem() {
        var selected: CatalogItem? = null
        var removals = 0
        composeRule.setContent {
            ContinueWatchingShelf(CatalogShelf("Continue Watching", listOf(series), false), { selected = it }, { removals++ })
        }

        poster("Series").performTouchInput { longClick() }
        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }

        composeRule.runOnIdle {
            assertEquals(series.id, selected?.id)
            assertFalse(selected!!.isContinueWatching)
            assertNull(selected?.continueWatchingVideoId)
            assertEquals(0, removals)
        }
    }

    @Test
    fun continueUsesCurrentEpisodeAndItsOwnCallback() {
        var currentSeries by mutableStateOf(series)
        var continued: CatalogItem? = null
        var detailOpens = 0
        composeRule.setContent {
            ContinueWatchingShelf(
                CatalogShelf("Continue Watching", listOf(currentSeries), false),
                { detailOpens++ },
                {},
                onContinueWatching = { continued = it },
            )
        }
        poster("Series").performTouchInput { longClick() }
        composeRule.runOnIdle { currentSeries = series.copy(continueWatchingVideoId = "next-episode") }
        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.runOnIdle {
            assertEquals("next-episode", continued?.continueWatchingVideoId)
            assertEquals(0, detailOpens)
        }
    }

    @Test
    fun menuClosesIfItsItemDisappearsOrAnotherScreenOpens() {
        var items by mutableStateOf(listOf(movie, channel))
        var enabled by mutableStateOf(true)
        var removals = 0
        composeRule.setContent {
            ContinueWatchingShelf(CatalogShelf("Continue Watching", items, false), {}, { removals++ }, actionsEnabled = enabled)
        }
        poster("Movie").performTouchInput { longClick() }
        composeRule.onNodeWithText("Remover de continuar assistindo").assertExists()
        composeRule.runOnIdle { items = listOf(channel) }
        composeRule.onNodeWithText("Remover de continuar assistindo").assertDoesNotExist()

        poster("Live channel").performTouchInput { longClick() }
        composeRule.onNodeWithText("Remover de continuar assistindo").assertExists()
        composeRule.runOnIdle { enabled = false }
        composeRule.onNodeWithText("Remover de continuar assistindo").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, removals) }
    }

    @Test
    fun movingFocusWhileOkIsHeldCancelsOpeningMenu() {
        var clicks = 0
        var timeoutMillis = 0L
        composeRule.setContent {
            timeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
            ContinueWatchingShelf(CatalogShelf("Continue Watching", listOf(movie, channel), false), { clicks++ }, {})
        }

        poster("Movie").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput {
                keyDown(Key.DirectionCenter)
                pressKey(Key.DirectionRight)
            }
        poster("Live channel").assertIsFocused()
        composeRule.mainClock.advanceTimeBy(timeoutMillis + 100)
        poster("Live channel").performKeyInput { keyUp(Key.DirectionCenter) }

        composeRule.onNodeWithText("Remover de continuar assistindo").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun removingLastItemRequestsFallbackFocus() {
        var items by mutableStateOf(listOf(series))
        var fallbackRequests = 0
        composeRule.setContent {
            ContinueWatchingShelf(
                CatalogShelf("Continue Watching", items, false),
                {},
                { items = emptyList() },
                { fallbackRequests++ },
            )
        }
        poster("Series").performTouchInput { longClick() }
        composeRule.onNodeWithText("Continuar assistindo").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }

        composeRule.runOnIdle { assertEquals(1, fallbackRequests) }
        poster("Series").assertDoesNotExist()
    }

    private fun poster(name: String) = composeRule.onNodeWithContentDescription(name)

    private fun dispatchToDialog(action: Int, keyCode: Int, repeat: Int = 0) {
        composeRule.runOnUiThread {
            val eventTime = SystemClock.uptimeMillis()
            ShadowDialog.getLatestDialog().window!!.decorView.dispatchKeyEvent(
                AndroidKeyEvent(eventTime, eventTime, action, keyCode, repeat),
            )
        }
        composeRule.waitForIdle()
    }
}
