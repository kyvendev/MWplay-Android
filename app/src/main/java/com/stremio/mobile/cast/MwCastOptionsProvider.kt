package com.stremio.mobile.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity

class MwCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setCastMediaOptions(
            CastMediaOptions.Builder()
                .setExpandedControllerActivityClassName(CastExpandedControllerActivity::class.java.name)
                .setNotificationOptions(
                    NotificationOptions.Builder()
                        .setTargetActivityClassName(CastExpandedControllerActivity::class.java.name)
                        .build(),
                )
                .build(),
        )
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

class CastExpandedControllerActivity : ExpandedControllerActivity()
