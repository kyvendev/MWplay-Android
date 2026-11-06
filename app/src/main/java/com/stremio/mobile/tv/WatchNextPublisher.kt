package com.stremio.mobile.tv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.stremio.mobile.MainActivity
import timber.log.Timber

/** One title of "Continuar assistindo" as the Google TV / Android TV home screen shows it. */
data class WatchNextEntry(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val positionMs: Long,
    val durationMs: Long,
    /** Newest first in the row. */
    val engagementOrder: Int,
)

/**
 * Keeps the home screen's "Play Next" row (the system Watch Next channel) in step with
 * "Continuar assistindo": a title is added or updated with its progress, and removed when it
 * leaves the list. A title the user removed from the row is not added back. Selecting it opens
 * MW Play and resumes it (same as "Continuar assistindo" in the app).
 */
class WatchNextPublisher(private val context: Context, private val now: () -> Long = System::currentTimeMillis) {

    data class Existing(val programId: Long, val browsable: Boolean)

    /** What to do with the row, given what is already there. */
    data class Plan(val insert: List<WatchNextEntry>, val update: Map<Long, WatchNextEntry>, val delete: List<Long>)

    companion object {
        const val EXTRA_OPEN_ID = "com.mwplay.extra.OPEN_ID"
        const val EXTRA_OPEN_TYPE = "com.mwplay.extra.OPEN_TYPE"
        const val EXTRA_OPEN_NAME = "com.mwplay.extra.OPEN_NAME"
        const val EXTRA_RESUME = "com.mwplay.extra.RESUME"

        fun keyOf(type: String, id: String) = "$type|$id"

        fun plan(entries: List<WatchNextEntry>, existing: Map<String, Existing>): Plan {
            val wanted = entries.filter { it.durationMs > 0 && it.positionMs in 1 until it.durationMs }.associateBy { keyOf(it.type, it.id) }
            val insert = wanted.filterKeys { it !in existing }.values.toList()
            // Removed by the user from the row: leave it out (Android TV guidelines).
            val update = wanted.filterKeys { existing[it]?.browsable == true }.entries.associate { existing.getValue(it.key).programId to it.value }
            val delete = existing.filterKeys { it !in wanted }.values.map { it.programId }
            return Plan(insert, update, delete)
        }
    }

    /** Never throws: the home screen is a convenience, never a reason to fail. */
    fun sync(entries: List<WatchNextEntry>) {
        runCatching {
            val plan = plan(entries, existing())
            val resolver = context.contentResolver
            plan.delete.forEach { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
            plan.update.forEach { (programId, entry) ->
                resolver.update(TvContractCompat.buildWatchNextProgramUri(programId), program(entry).toContentValues(), null, null)
            }
            plan.insert.forEach { resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program(it).toContentValues()) }
        }.onFailure { Timber.w(it, "Watch Next sync failed") }
    }

    /** The app's own programs (the provider only returns those; WHERE clauses are not allowed). */
    @SuppressLint("RestrictedApi")
    private fun existing(): Map<String, Existing> {
        val result = HashMap<String, Existing>()
        context.contentResolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val program = WatchNextProgram.fromCursor(cursor)
                val key = program.internalProviderId ?: continue
                result[key] = Existing(program.id, program.isBrowsable)
            }
        }
        return result
    }

    private fun program(entry: WatchNextEntry): WatchNextProgram {
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_ID, entry.id)
            .putExtra(EXTRA_OPEN_TYPE, entry.type)
            .putExtra(EXTRA_OPEN_NAME, entry.name)
            .putExtra(EXTRA_RESUME, true)
        return WatchNextProgram.Builder()
            .setType(if (entry.type == "movie") TvContractCompat.WatchNextPrograms.TYPE_MOVIE else TvContractCompat.WatchNextPrograms.TYPE_TV_SERIES)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            // Keeps the app's own order (most recent first).
            .setLastEngagementTimeUtcMillis(now() - entry.engagementOrder * 1_000L)
            .setLastPlaybackPositionMillis(entry.positionMs.toInt())
            .setDurationMillis(entry.durationMs.toInt())
            .setTitle(entry.name)
            .apply { entry.poster?.let { setPosterArtUri(Uri.parse(it)) } }
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
            .setIntent(open)
            .setInternalProviderId(keyOf(entry.type, entry.id))
            .build()
    }
}
