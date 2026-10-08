package com.stremio.mobile.presentation.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.presentation.navigation.AppView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the same rail/content scaffold used by BoardScreen on a wide TV screen. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = "w1280dp-h720dp-land-television-mdpi",
    application = Application::class,
)
@OptIn(ExperimentalTestApi::class)
class TvNavigationRailTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var selected by mutableStateOf(AppView.Home)
    private var detailsOpen by mutableStateOf(false)
    private val selectedViews = mutableListOf<AppView>()
    private val openedItems = mutableListOf<String>()
    private var filterClicks = 0

    @Test
    fun railAndCollapsedOpenerStayAtLeftEdgeOfLandscapeContent() {
        setBoardContent()
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Test must use a landscape TV viewport: $screen", screen.width > screen.height)
        assertTrue("Test must cover a wide TV viewport: $screen", screen.width >= 1200f)
        val menuItem = composeRule.onNodeWithText("Início").fetchSemanticsNode().boundsInRoot
        assertTrue("Expanded rail must be at the left edge: $menuItem", menuItem.left < 122f)

        composeRule.onNodeWithText("Início").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("filter-type").assertIsFocused()
        val opener = composeRule.onNodeWithContentDescription("Abrir menu").fetchSemanticsNode().boundsInRoot
        assertTrue("Collapsed rail must stay at the left edge: $opener", opener.right < 122f)
        val firstPoster = poster("First movie").fetchSemanticsNode().boundsInRoot
        assertTrue("Rail must not cover a poster: $firstPoster / $opener", firstPoster.left > opener.right)
    }

    @Test
    fun rightFromExpandedRailReachesFiltersAndPostersAndOkOpensMovie() {
        setBoardContent()
        composeRule.onNodeWithText("Início").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("filter-type").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(1, filterClicks) }

        composeRule.onNodeWithTag("filter-type")
            .performKeyInput { pressKey(Key.DirectionDown) }
        poster("First movie").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        poster("Second movie").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(listOf("second"), openedItems) }
    }

    @Test
    fun rightFromCollapsedOpenerRestoresContentInsteadOfTrappingFocusOnArrow() {
        setBoardContent()
        enterContent()
        composeRule.onNodeWithTag("filter-type")
            .performKeyInput { pressKey(Key.DirectionDown) }
        poster("First movie").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithContentDescription("Abrir menu").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        poster("First movie").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(listOf("first"), openedItems) }
    }

    @Test
    fun reopeningMenuAndClosingItRestoresThePreviouslyFocusedPoster() {
        setBoardContent()
        enterContent()
        composeRule.onNodeWithTag("filter-type")
            .performKeyInput { pressKey(Key.DirectionDown) }
        poster("First movie").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithContentDescription("Abrir menu").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Início").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        poster("First movie").assertIsFocused()
        composeRule.runOnIdle { assertTrue(selectedViews.isEmpty()) }
    }

    @Test
    fun choosingDiscoverClosesMenuAndNewContentCanBeUsedWithoutExtraFocusRequests() {
        setBoardContent()
        composeRule.onNodeWithText("Início").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Descobrir").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithText("Fechar").assertDoesNotExist()
        composeRule.onNodeWithTag("filter-type").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("filter-sort").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            assertEquals(AppView.Discover, selected)
            assertEquals(listOf(AppView.Discover), selectedViews)
            assertEquals(1, filterClicks)
        }
        composeRule.onNodeWithTag("filter-sort")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("filter-type").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        poster("First movie").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(listOf("first"), openedItems) }
    }

    @Test
    fun backClosesExpandedRailAndEntersContentWithoutSwitchingSections() {
        setBoardContent()
        composeRule.onNodeWithText("Início").assertIsFocused()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("Fechar").assertDoesNotExist()
        composeRule.onNodeWithTag("filter-type").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(AppView.Home, selected)
            assertTrue(selectedViews.isEmpty())
        }
    }

    @Test
    fun closingButtonCollapsesMenuAndReturnsToContent() {
        setBoardContent()
        composeRule.onNodeWithText("Início").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Fechar").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithTag("filter-type").assertIsFocused()
        composeRule.onNodeWithContentDescription("Abrir menu").assertExists()
    }

    @Test
    fun overlayFocusAndItsRestorationAreNotStolenByCollapsedRail() {
        setBoardContent()
        enterContent()
        composeRule.onNodeWithTag("filter-type")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("filter-sort").assertIsFocused()
        composeRule.runOnIdle { detailsOpen = true }
        composeRule.onNodeWithText("Close details").assertIsFocused()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithText("Close details").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithTag("filter-sort").assertIsFocused()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag("filter-sort").assertIsFocused()
    }

    private fun enterContent() {
        composeRule.onNodeWithText("Início").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("filter-type").assertIsFocused()
    }

    private fun poster(name: String) = composeRule.onNodeWithContentDescription(name)

    private fun setBoardContent() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalGlobalUiTheme provides GlobalUiTheme(style = "modern", hapticsEnabled = false),
            ) {
                TvNavigationScaffold(
                    selectedView = selected,
                    backdrop = null,
                    onSelect = { selected = it; selectedViews += it },
                    contentFocusRequester = remember { FocusRequester() },
                    navigationEnabled = !detailsOpen,
                ) { contentModifier ->
                    BoardCatalogContent(contentModifier)
                }
                if (detailsOpen) {
                    val closeFocus = remember { FocusRequester() }
                    Dialog(onDismissRequest = { detailsOpen = false }) {
                        ThemedButton(
                            "Close details",
                            { detailsOpen = false },
                            modifier = Modifier.focusRequester(closeFocus),
                        )
                        LaunchedEffect(Unit) {
                            withFrameNanos { }
                            closeFocus.requestFocus()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BoardCatalogContent(contentModifier: Modifier) {
        LazyColumn(
            modifier = contentModifier.fillMaxSize().testTag("content"),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "heading-${selected.name}") { Text(selected.name, modifier = Modifier.padding(16.dp)) }
            item(key = "filters-${selected.name}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ThemedButton(
                        if (selected == AppView.Discover) "Movies" else "Browse",
                        { filterClicks++ },
                        modifier = Modifier.width(120.dp).testTag("filter-type"),
                    )
                    ThemedButton(
                        "Popular",
                        { filterClicks++ },
                        modifier = Modifier.width(120.dp).testTag("filter-sort"),
                    )
                }
            }
            item(key = "posters-${selected.name}") {
                PosterShelf(
                    CatalogShelf(
                        "Popular movies",
                        listOf(
                            CatalogItem("first", "movie", "First movie", null, null, null, null),
                            CatalogItem("second", "movie", "Second movie", null, null, null, null),
                        ),
                        false,
                    ),
                    ShelfMode.Movie,
                    { openedItems += it.id },
                )
            }
        }
    }
}
