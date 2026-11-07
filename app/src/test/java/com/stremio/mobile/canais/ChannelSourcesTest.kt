package com.stremio.mobile.canais

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.stremio.core.models.CatalogWithFilters
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.mobile.data.model.TvCatalogPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MW Play Canais lists only Best Cine's channels; other addons' TV catalogs are left out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChannelSourcesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val other = ResourceRequest("https://outro-iptv.example/manifest.json", ResourcePath("catalog", "tv", "iptv"))
    private val requested = mutableListOf<ResourceRequest>()

    /** Best Cine's two catalogs plus another addon's "IPTV Mundo"; [otherSelected] opens that one. */
    private fun filters(otherSelected: Boolean, withBestCine: Boolean = true): CatalogWithFilters {
        val base = ChannelsFixtures.filters()
        val bestCine = if (withBestCine) base.selectable.catalogs.map { it.copy(selected = it.selected && !otherSelected) } else emptyList()
        return base.copy(
            selected = CatalogWithFilters.Selected(if (otherSelected) other else ChannelsFixtures.all),
            selectable = base.selectable.copy(catalogs = bestCine + CatalogWithFilters.SelectableCatalog("IPTV Mundo", otherSelected, other)),
        )
    }

    private fun show(filters: CatalogWithFilters) = composeRule.setContent {
        ChannelsScreen(
            filters = filters,
            catalog = ChannelsFixtures.shelf(),
            favorites = emptyList(),
            onLoadChannels = {},
            onSelectRequest = { requested += it },
            onPlay = { _, _ -> },
            onToggleFavorite = { false },
            onOpenSettings = {},
            isChannelSource = { base -> TvCatalogPolicy.isPreferredProvider(base) },
        )
    }

    @Test
    fun anotherAddonsCatalogIsHiddenAndBestCineChannelsShow() {
        show(filters(otherSelected = false))
        composeRule.onNodeWithText("Globo").assertIsDisplayed()
        composeRule.onAllNodesWithText("IPTV Mundo").assertCountEquals(0)
        composeRule.runOnIdle { assertTrue(requested.isEmpty()) }
    }

    @Test
    fun anotherAddonsCatalogOpenSwitchesToBestCine() {
        show(filters(otherSelected = true))
        composeRule.runOnIdle { assertEquals(listOf(ChannelsFixtures.all), requested) }
        // Its channels are not listed while switching.
        composeRule.onAllNodesWithText("Globo").assertCountEquals(0)
    }

    @Test
    fun withoutBestCineTheScreenSaysSo() {
        show(filters(otherSelected = true, withBestCine = false))
        composeRule.onNodeWithText("Os canais vêm do addon BestCine. Instale o BestCine na sua conta para ver os canais.").assertExists()
        composeRule.onAllNodesWithText("Globo").assertCountEquals(0)
    }
}
