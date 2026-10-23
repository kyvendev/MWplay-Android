package com.stremio.mobile.data.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.stremio.core.models.StreamingServer
import com.stremio.core.types.profile.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FirstRunDefaultsTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(FirstRunDefaults.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun open(fresh: Boolean) = FirstRunDefaults(prefs) { fresh }

    @Test
    fun freshInstallGetsAllDefaults() {
        val defaults = open(fresh = true)
        assertTrue(defaults.profilePending)
        assertTrue(defaults.serverPending)

        val profile = defaults.applyTo(profile())
        assertEquals("por", profile.interfaceLanguage)
        assertEquals("por", profile.audioLanguage)
        assertEquals("pob", profile.subtitlesLanguage)
        // Unrelated preferences are kept.
        assertEquals(150, profile.subtitlesSize)
        assertEquals("exo", profile.playerType)

        val server = defaults.applyTo(server())
        assertEquals(5_368_709_120.0, server.cacheSize!!, 0.0)
        assertEquals(800L, server.btMaxConnections)
        assertEquals(true, server.proxyStreamsEnabled)
        assertFalse(FirstRunDefaults.SEEDING_ENABLED)
    }

    @Test
    fun pendingDefaultsSurviveARestartUntilApplied() {
        open(fresh = true)
        // Restart before the server was reachable: still pending.
        val restarted = open(fresh = true)
        assertTrue(restarted.serverPending)
        restarted.markServerApplied()
        restarted.markProfileApplied()
        val again = open(fresh = true)
        assertFalse(again.serverPending)
        assertFalse(again.profilePending)
    }

    @Test
    fun reopeningNeverOverwritesUserChanges() {
        open(fresh = true).apply { markProfileApplied(); markServerApplied() }
        // Even if the install still looks "fresh" (same install timestamp), the decision is stored.
        repeat(3) {
            val reopened = open(fresh = true)
            assertFalse(reopened.profilePending)
            assertFalse(reopened.serverPending)
        }
    }

    @Test
    fun updatingAnExistingInstallDoesNotApplyDefaults() {
        val existing = open(fresh = false)
        assertFalse(existing.profilePending)
        assertFalse(existing.serverPending)
        // Later starts keep the first decision.
        assertFalse(open(fresh = true).profilePending)
    }

    @Test
    fun invalidStoredValuesAndDetectionErrorsAreSafe() {
        prefs.edit().putString("decided_v1", "garbage").putInt("server_pending_v1", 7).commit()
        val defaults = FirstRunDefaults(prefs) { error("package manager unavailable") }
        // No crash; when in doubt the user's current settings are kept.
        assertFalse(defaults.profilePending)
        assertFalse(defaults.serverPending)
    }

    @Test
    fun serverReceivesTheDefaultValues() {
        val defaults = open(fresh = true)
        val body = serverSettingsPayload(defaults.applyTo(server()), FirstRunDefaults.SEEDING_ENABLED)
        assertEquals(5_368_709_120.0, body.getDouble("cacheSize"), 0.0)
        assertEquals(800L, body.getLong("btMaxConnections"))
        assertEquals(false, body.getBoolean("seedingEnabled"))
        // Other server values are passed through unchanged.
        assertEquals(200L, body.getLong("btHandshakeTimeout"))
        assertEquals("/data/app", body.getString("appPath"))
    }

    private fun server() = StreamingServer.Settings(
        appPath = "/data/app",
        cacheRoot = "/data/cache",
        serverVersion = "1.0",
        cacheSize = 10_737_418_240.0,
        proxyStreamsEnabled = true,
        btMaxConnections = 200L,
        btHandshakeTimeout = 200L,
        btRequestTimeout = 10L,
        btDownloadSpeedSoftLimit = 1.0,
        btDownloadSpeedHardLimit = 2.0,
        btMinPeersForStable = 10L,
    )

    private fun profile() = Profile.Settings(
        interfaceLanguage = "eng",
        hideSpoilers = false,
        streamingServerUrl = "http://127.0.0.1:11470/",
        bingeWatching = true,
        playInBackground = true,
        hardwareDecoding = true,
        audioPassthrough = false,
        audioLanguage = "eng",
        subtitlesAutoSelect = true,
        subtitlesLanguage = "eng",
        subtitlesSize = 150,
        subtitlesFont = "Roboto",
        subtitlesBold = false,
        subtitlesOffset = 5,
        subtitlesTextColor = "#FFFFFF",
        subtitlesBackgroundColor = "#00000000",
        subtitlesOutlineColor = "#000000",
        subtitlesOpacity = 100,
        escExitFullscreen = true,
        seekTimeDuration = 10_000L,
        seekShortTimeDuration = 3_000L,
        pauseOnMinimize = false,
        playerType = "exo",
        frameRateMatchingStrategy = Profile.FrameRateMatchingStrategy.DISABLED,
        nextVideoNotificationDuration = 35_000L,
        surroundSound = false,
        sendCrashReports = true,
        serverInForeground = false,
        quitOnClose = false,
        gamepadSupport = false,
        assSubtitlesStyling = true,
    )
}
