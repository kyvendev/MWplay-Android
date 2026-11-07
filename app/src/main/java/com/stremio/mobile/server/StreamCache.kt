package com.stremio.mobile.server

import android.content.Context
import java.io.File

/**
 * The torrent data the streaming server downloads while something plays. It is only needed during
 * playback, so it is deleted once the player closes and when the app starts, to keep phones and TVs
 * with little storage from filling up.
 */
object StreamCache {
    /** Folders of the server's cache root (see JniStreamingServerController) that hold downloaded data. */
    private val DATA_DIRS = listOf("torrent-cache", "libtorrent-downloads", "rqbit-downloads", "disk-cache")

    fun root(context: Context): File = File(context.cacheDir, "stream-server")

    /** Deletes the downloaded data and keeps the folders the server expects. Returns the bytes freed. */
    fun clear(root: File): Long {
        var freed = 0L
        for (name in DATA_DIRS) {
            val dir = File(root, name)
            dir.listFiles()?.forEach { child ->
                val size = child.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                if (child.deleteRecursively()) freed += size
            }
        }
        return freed
    }

    fun clear(context: Context): Long = clear(root(context))
}
