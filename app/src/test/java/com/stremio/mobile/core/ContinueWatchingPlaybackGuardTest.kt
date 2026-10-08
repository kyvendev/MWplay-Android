package com.stremio.mobile.core

import com.stremio.core.runtime.msg.Action
import com.stremio.core.runtime.msg.ActionCtx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingPlaybackGuardTest {
    @Test
    fun removingThePlayingMovieSeriesOrLiveChannelSuppressesOnlyItsReports() {
        listOf("movie" to "tt123", "series" to "tt456", "tv" to "live:espn").forEach { (type, id) ->
            val guard = ContinueWatchingPlaybackGuard()
            val active = ContinueWatchingPlaybackGuard.ItemKey(id, type)
            guard.suppressIfActive(active, active)
            assertFalse(guard.allowsReports(active))
            assertTrue(guard.allowsReports(ContinueWatchingPlaybackGuard.ItemKey("other:$id", type)))
            assertTrue(guard.allowsReports(ContinueWatchingPlaybackGuard.ItemKey(id, "other")))
        }
    }

    @Test
    fun removingAnotherCardDoesNotSuppressCurrentPlayback() {
        val guard = ContinueWatchingPlaybackGuard()
        val active = ContinueWatchingPlaybackGuard.ItemKey("playing", "movie")
        val removed = ContinueWatchingPlaybackGuard.ItemKey("another", "series")
        guard.suppressIfActive(removed, active)
        assertTrue(guard.allowsReports(active))
    }

    @Test
    fun removingAnotherCardKeepsPreviouslyRemovedActivePlaybackSuppressed() {
        val guard = ContinueWatchingPlaybackGuard()
        val active = ContinueWatchingPlaybackGuard.ItemKey("playing", "movie")
        guard.suppressIfActive(active, active)
        guard.suppressIfActive(ContinueWatchingPlaybackGuard.ItemKey("another", "series"), active)
        assertFalse(guard.allowsReports(active))
    }

    @Test
    fun selectingEvenTheSameItemExplicitlyResumesReports() {
        val guard = ContinueWatchingPlaybackGuard()
        val active = ContinueWatchingPlaybackGuard.ItemKey("playing", "movie")
        guard.suppressIfActive(active, active)
        guard.onNewSelection()
        assertTrue(guard.allowsReports(active))
    }

    @Test
    fun failedRemovalRestoresThePreviousReportingPolicy() {
        val guard = ContinueWatchingPlaybackGuard()
        val active = ContinueWatchingPlaybackGuard.ItemKey("playing", "movie")
        val before = guard.snapshot()
        guard.suppressIfActive(active, active)
        guard.restoreAfterFailure(before)
        assertTrue(guard.allowsReports(active))
    }

    @Test
    fun anOlderFailedRemovalCannotSuppressANewSelection() {
        val guard = ContinueWatchingPlaybackGuard()
        val active = ContinueWatchingPlaybackGuard.ItemKey("playing", "movie")
        guard.suppressIfActive(active, active)
        val before = guard.snapshot()
        guard.onNewSelection()
        guard.restoreAfterFailure(before)
        assertTrue(guard.allowsReports(active))
    }

    @Test
    fun removalUsesResumeAndNotificationActionsWithTheWholeItemId() {
        listOf("tt123", "tt456", "live:espn").forEach { id ->
            val args = continueWatchingRemovalActions(id).map { action ->
                (action.type as Action.Type.Ctx).value.args
            }
            assertEquals(listOf(ActionCtx.Args.RewindLibraryItem(id), ActionCtx.Args.DismissNotificationItem(id)), args)
            assertTrue(args.none { it is ActionCtx.Args.RemoveFromLibrary || it is ActionCtx.Args.LibraryItemMarkAsWatched })
        }
    }
}
