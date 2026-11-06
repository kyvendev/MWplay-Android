package com.stremio.mobile.presentation.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens a trailer in the YouTube app (phones and Android TV), or in any app that handles a
 * YouTube link. The trailer never plays inside MW Play.
 */
object TrailerLauncher {
    fun intents(youTubeId: String): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youTubeId")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youTubeId")),
    ).onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    fun open(context: Context, youTubeId: String) {
        for (intent in intents(youTubeId)) {
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next way of opening it.
            }
        }
        Toast.makeText(context, "Instale o YouTube para ver o trailer.", Toast.LENGTH_LONG).show()
    }
}
