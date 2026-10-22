package com.stremio.mobile.presentation.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stremio.mobile.data.model.CatalogShelf
import com.stremio.mobile.data.repository.SearchHistoryPolicy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi", application = Application::class)
class SearchHistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var history by mutableStateOf(listOf("Matrix", "Breaking Bad"))
    private var query by mutableStateOf("")
    private var submitted = 0

    private fun show() {
        composeRule.setContent {
            SearchResultsScreen(
                query = query,
                results = CatalogShelf(title = "Search", isLoading = false),
                shelves = emptyList(),
                history = history,
                onQueryChange = { query = it },
                onSubmitSearch = { submitted++ },
                onRemoveHistory = { history = SearchHistoryPolicy.remove(history, it) },
                onOpenDetails = {},
                onOpenDiscoverCatalog = { _, _ -> },
                onBack = {},
            )
        }
    }

    @Test
    fun emptyFieldShowsRecentSearchesAndXRemovesOne() {
        show()
        composeRule.onNodeWithText("Buscas recentes").assertIsDisplayed()
        composeRule.onNodeWithText("Matrix").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remover \"Matrix\" do histórico").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("Breaking Bad"), history)
        composeRule.onNodeWithText("Matrix").assertDoesNotExist()
    }

    @Test
    fun tappingAnEntrySearchesItAgain() {
        show()
        composeRule.onNodeWithText("Breaking Bad").performClick()
        composeRule.waitForIdle()
        assertEquals("Breaking Bad", query)
    }

    @Test
    fun noHistoryShowsAHintInsteadOfAnEmptyResult() {
        history = emptyList()
        show()
        composeRule.onNodeWithText("Digite o nome de um filme ou série.").assertIsDisplayed()
        composeRule.onNodeWithText("Nenhum resultado para \"\".").assertDoesNotExist()
    }
}
