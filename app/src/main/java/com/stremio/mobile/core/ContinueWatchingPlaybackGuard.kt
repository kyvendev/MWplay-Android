package com.stremio.mobile.core

import com.stremio.core.runtime.msg.Action
import com.stremio.core.runtime.msg.ActionCtx

/** A removed card stays removed while its current local/Cast playback continues. */
internal class ContinueWatchingPlaybackGuard {
    data class ItemKey(val id: String, val type: String)
    data class Snapshot(val selectionGeneration: Long, val suppressedItem: ItemKey?)

    private var selectionGeneration = 0L

    @Volatile
    var suppressedItem: ItemKey? = null
        private set

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(selectionGeneration, suppressedItem)

    @Synchronized
    fun suppressIfActive(removedItem: ItemKey, activeItem: ItemKey?) {
        if (removedItem == activeItem) suppressedItem = removedItem
    }

    fun allowsReports(activeItem: ItemKey?): Boolean =
        suppressedItem == null || suppressedItem != activeItem

    @Synchronized
    fun onNewSelection() {
        selectionGeneration += 1
        suppressedItem = null
    }

    @Synchronized
    fun restoreAfterFailure(previous: Snapshot) {
        // A slow failed removal must never suppress playback selected in the meantime.
        if (selectionGeneration == previous.selectionGeneration) {
            suppressedItem = previous.suppressedItem
        }
    }
}

/** Neither action changes library membership or marks episodes as watched. */
internal fun continueWatchingRemovalActions(id: String): List<Action> = listOf(
    Action(Action.Type.Ctx(ActionCtx(ActionCtx.Args.RewindLibraryItem(id)))),
    Action(Action.Type.Ctx(ActionCtx(ActionCtx.Args.DismissNotificationItem(id)))),
)
