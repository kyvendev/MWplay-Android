package com.stremio.mobile.diagnostics

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Crashlytics, or null when Firebase is not configured for this app (MW Play Canais). */
fun crashlyticsOrNull(): FirebaseCrashlytics? = runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
