package com.stremio.mobile.presentation.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.stremio.core.types.addon.ExtraValue
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.data.model.TvCategories
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The TV tab's categories with the remote (D-pad) and with touch/mouse. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-television-mdpi", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class TvCategoryChipsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val all = ResourceRequest("https://addon.example/", ResourcePath("catalog", "tv", "canais"))
    private val sports = all.copy(path = all.path.copy(extra = listOf(ExtraValue("genre", "Esportes"))))
    private val news = all.copy(path = all.path.copy(extra = listOf(ExtraValue("genre", "Notícias"))))
    private val row = TvCategories.build(
        options = listOf(
            TvCategories.Option(null, true, all),
            TvCategories.Option("Esportes", false, sports),
            TvCategories.Option("Notícias", false, news),
        ),
        sportsByMetadata = false,
        label = { it },
    )
    private val picked = mutableListOf<ResourceRequest>()

    private fun setContent() = composeRule.setContent { TvCategoryChipsRow(row = row, onSelect = { picked += it.request }) }

    @Test
    fun remoteMovesFocusAcrossCategoriesAndOkSelects() {
        setContent()
        composeRule.onNodeWithText("Todos os canais").assertIsSelected().performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithText("Todos os canais").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Esportes").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle { assertEquals(listOf(sports), picked) }
        composeRule.onNodeWithText("Esportes").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Notícias").assertIsFocused()
    }

    @Test
    fun touchOrMouseSelectsToo() {
        setContent()
        composeRule.onNodeWithText("Esportes").performClick()
        composeRule.runOnIdle { assertEquals(listOf(sports), picked) }
    }
}
