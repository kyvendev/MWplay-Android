package com.stremio.mobile.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stremio.mobile.MainActivity
import com.stremio.mobile.MainApplication
import com.stremio.mobile.R
import com.stremio.mobile.core.StremioCore
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** A series in the library with episodes the user has not seen yet (Stremio's own notifications). */
data class NewEpisodes(val id: String, val type: String, val name: String, val count: Long)

/**
 * Which series deserve a notification: only when the number of new episodes grew since the last
 * one, so the same episode is announced once. On the very first run the current state is only
 * recorded (no burst of notifications for episodes released long ago).
 */
object NewEpisodeRules {
    data class Result(val toNotify: List<NewEpisodes>, val remembered: Map<String, Long>)

    fun decide(current: List<NewEpisodes>, remembered: Map<String, Long>, firstRun: Boolean): Result {
        val withNew = current.filter { it.count > 0 }
        val next = withNew.associate { it.id to it.count }
        val toNotify = if (firstRun) emptyList() else withNew.filter { it.count > (remembered[it.id] ?: 0L) }
        return Result(toNotify, next)
    }
}

/**
 * Asks Stremio for new episodes of the series in the library and shows "Novo episódio" notifications.
 * Runs when the app opens and every 12 hours in the background (WorkManager, only with internet).
 * Tapping a notification opens the series in the app.
 */
class NewEpisodeNotifier(private val context: Context) {
    private val preferences = context.getSharedPreferences("mwplay_new_episodes", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    suspend fun check(core: StremioCore) {
        if (!enabled) return
        runCatching { core.pullNotifications() }.onFailure { Timber.w(it, "New episodes: pull failed") }
        val current = runCatching {
            core.getContinueWatchingPreview().libraryItems.map { NewEpisodes(it.id, it.type, it.name, it.notifications) }
        }.getOrElse {
            Timber.w(it, "New episodes: library unavailable")
            return
        }
        val remembered = preferences.all.filterKeys { it.startsWith(PREFIX) }
            .mapNotNull { (key, value) -> (value as? Long)?.let { key.removePrefix(PREFIX) to it } }.toMap()
        val result = NewEpisodeRules.decide(current, remembered, firstRun = !preferences.getBoolean(KEY_BASELINE, false))
        preferences.edit().apply {
            remembered.keys.forEach { remove(PREFIX + it) }
            result.remembered.forEach { (id, count) -> putLong(PREFIX + id, count) }
            putBoolean(KEY_BASELINE, true)
        }.apply()
        result.toNotify.forEach(::show)
    }

    private fun show(item: NewEpisodes) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_ID, item.id)
            .putExtra(EXTRA_OPEN_TYPE, item.type)
            .putExtra(EXTRA_OPEN_NAME, item.name)
        val pending = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (item.count == 1L) "Um episódio novo para assistir" else "${item.count} episódios novos para assistir"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Novo episódio de ${item.name}")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Novos episódios", NotificationManager.IMPORTANCE_DEFAULT))
    }

    companion object {
        const val EXTRA_OPEN_ID = "com.mwplay.extra.OPEN_ID"
        const val EXTRA_OPEN_TYPE = "com.mwplay.extra.OPEN_TYPE"
        const val EXTRA_OPEN_NAME = "com.mwplay.extra.OPEN_NAME"
        private const val CHANNEL_ID = "new_episodes"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BASELINE = "baseline_done"
        private const val PREFIX = "count:"
        private const val WORK_NAME = "mwplay-new-episodes"

        /** Every 12 hours with internet; kept across restarts by WorkManager. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewEpisodesWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

class NewEpisodesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MainApplication ?: return Result.success()
        NewEpisodeNotifier(applicationContext).check(app.container.core)
        return Result.success()
    }
}
