package com.stremio.mobile.presentation.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContinueWatchingShelfTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val movie = item("shared-id", "movie", "Filme")
    private val channel = item("shared-id", "channel", "ESPN")
    private val series = item("series", "series", "Série").copy(continueWatchingVideoId = "series:1:2")

    @Test
    fun tapResumesPlaybackWithoutOpeningTheMenu() {
        var resumed: CatalogItem? = null
        var details: CatalogItem? = null
        setShelf(onContinue = { resumed = it }, onDetails = { details = it })

        composeRule.onNodeWithContentDescription(movie.name).performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(movie, resumed)
            assertNull(details)
        }
        composeRule.onNodeWithText("Excluir da lista").assertDoesNotExist()
    }

    @Test
    fun longPressOffersOnlyDetailsAndRemoveAndRemovesTheSelectedChannelOnly() {
        var resumed: CatalogItem? = null
        var removed: CatalogItem? = null
        setShelf(onContinue = { resumed = it }, onRemove = { removed = it })

        composeRule.onNodeWithContentDescription(channel.name).performTouchInput { longClick() }
        composeRule.onNodeWithText(channel.name).assertIsDisplayed()
        composeRule.onNodeWithText("Detalhes").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(resumed) }
        composeRule.onNodeWithText("Excluir da lista").performClick()

        composeRule.runOnIdle {
            assertEquals(channel, removed)
            assertNull(resumed)
        }
        composeRule.onNodeWithText("Cancelar").assertDoesNotExist()
    }

    @Test
    fun detailsUsesTheLatestEpisodeAfterShelfRefresh() {
        var details: CatalogItem? = null
        var shelf by mutableStateOf(CatalogShelf(title = "Continuar assistindo", items = listOf(series), isLoading = false))
        composeRule.setContent {
            MaterialTheme {
                ContinueWatchingShelf(
                    shelf,
                    onContinueWatching = {},
                    onOpenDetails = { details = it },
                    onRemove = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(series.name).performScrollTo().performTouchInput { longClick() }
        val nextEpisode = series.copy(continueWatchingVideoId = "series:1:3", progress = 0.2f)
        composeRule.runOnIdle { shelf = shelf.copy(items = listOf(nextEpisode)) }
        composeRule.onNodeWithText("Detalhes").performClick()

        composeRule.runOnIdle { assertEquals(nextEpisode.copy(isContinueWatching = false), details) }
        composeRule.onNodeWithText("Cancelar").assertDoesNotExist()
    }

    @Test
    fun detailsOpensNormalDetailsWithoutResumeAndCancelRunsNoAction() {
        var details: CatalogItem? = null
        var resumed: CatalogItem? = null
        var removed: CatalogItem? = null
        setShelf(onContinue = { resumed = it }, onDetails = { details = it }, onRemove = { removed = it })

        composeRule.onNodeWithContentDescription(series.name).performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.runOnIdle {
            assertNull(resumed)
            assertNull(details)
            assertNull(removed)
        }

        composeRule.onNodeWithContentDescription(series.name).performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithText("Detalhes").performClick()
        composeRule.runOnIdle {
            assertEquals(series.id, details?.id)
            assertFalse(details!!.isContinueWatching)
            assertNull(resumed)
            assertNull(removed)
        }
    }

    @Test
    fun externalRemovalDismissesTheMenuInsteadOfUsingAStaleItem() {
        var shelf by mutableStateOf(CatalogShelf(title = "Continuar assistindo", items = listOf(movie), isLoading = false))
        composeRule.setContent {
            MaterialTheme {
                ContinueWatchingShelf(shelf, {}, {}, {})
            }
        }
        composeRule.onNodeWithContentDescription(movie.name).performTouchInput { longClick() }
        composeRule.onNodeWithText("Excluir da lista").assertIsDisplayed()

        composeRule.runOnIdle { shelf = shelf.copy(items = emptyList()) }

        composeRule.onNodeWithText("Excluir da lista").assertDoesNotExist()
    }

    @Test
    fun openingAnotherAppSurfaceDismissesMenuWithoutExecutingAnAction() {
        var enabled by mutableStateOf(true)
        var removals = 0
        composeRule.setContent {
            MaterialTheme {
                ContinueWatchingShelf(
                    shelf = CatalogShelf(title = "Continuar assistindo", items = listOf(movie), isLoading = false),
                    onContinueWatching = {},
                    onOpenDetails = {},
                    onRemove = { removals++ },
                    actionsEnabled = enabled,
                )
            }
        }
        composeRule.onNodeWithContentDescription(movie.name).performTouchInput { longClick() }
        composeRule.onNodeWithText("Excluir da lista").assertIsDisplayed()

        composeRule.runOnIdle { enabled = false }

        composeRule.onNodeWithText("Excluir da lista").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, removals) }
    }

    private fun setShelf(
        onContinue: (CatalogItem) -> Unit = {},
        onDetails: (CatalogItem) -> Unit = {},
        onRemove: (CatalogItem) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalGlobalUiTheme provides GlobalUiTheme(style = "modern", hapticsEnabled = false),
                ) {
                    ContinueWatchingShelf(
                        shelf = CatalogShelf(title = "Continuar assistindo", items = listOf(movie, channel, series), isLoading = false),
                        onContinueWatching = onContinue,
                        onOpenDetails = onDetails,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }

    private fun item(id: String, type: String, name: String) = CatalogItem(
        id = id,
        type = type,
        name = name,
        poster = null,
        background = null,
        releaseInfo = null,
        imdbRating = null,
        progress = 0.5f,
        isContinueWatching = true,
    )
}
