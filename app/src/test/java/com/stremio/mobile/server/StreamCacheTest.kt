package com.stremio.mobile.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StreamCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(root: File, path: String, bytes: Int) = File(root, path).apply {
        parentFile!!.mkdirs()
        writeBytes(ByteArray(bytes))
    }

    @Test
    fun clearsDownloadedTorrentDataAndKeepsTheFolders() {
        val root = tmp.newFolder("stream-server")
        file(root, "libtorrent-downloads/Movie (2024)/movie.mkv", 1_000)
        file(root, "libtorrent-downloads/abc.parts", 200)
        file(root, "disk-cache/piece-1", 300)
        val logs = file(root, "logs/server.log", 50)

        val freed = StreamCache.clear(root)

        assertEquals(1_500L, freed)
        assertTrue(File(root, "libtorrent-downloads").isDirectory)
        assertTrue(File(root, "disk-cache").isDirectory)
        assertEquals(0, File(root, "libtorrent-downloads").listFiles()!!.size)
        assertEquals(0, File(root, "disk-cache").listFiles()!!.size)
        assertTrue("other server files stay", logs.exists())
    }

    @Test
    fun nothingToClearIsFine() {
        val root = File(tmp.root, "missing")
        assertEquals(0L, StreamCache.clear(root))
        assertFalse(root.exists())
    }
}
