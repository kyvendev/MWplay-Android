package com.stremio.mobile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.stremio.mobile.auth.FacebookLoginBridge
import com.stremio.mobile.presentation.screens.StremioMobileApp
import com.stremio.mobile.presentation.viewmodel.MainViewModel
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    @Volatile
    private var sessionValidationInFlight = false

    private val viewModel: MainViewModel by viewModels {
        val app = application as MainApplication
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    authRepository = app.container.authRepository,
                    boardRepository = app.container.boardRepository,
                    catalogRepository = app.container.catalogRepository,
                    addonRepository = app.container.addonRepository,
                    playbackRepository = app.container.playbackRepository,
                    updateRepository = app.container.updateRepository,
                    apkInstaller = app.container.apkInstaller,
                    serverController = app.container.serverController,
                    core = app.container.core,
                    appContext = app.applicationContext,
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            viewModel.sessionRestoring.value
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestNotificationPermission()
        viewModel.acceptIntent(intent)

        setContent {
            StremioMobileApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.acceptIntent(intent)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (FacebookLoginBridge.callbackManager.onActivityResult(requestCode, resultCode, data)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
        validateRemoteSession()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    /**
     * Revalidates the saved Stremio auth key whenever MW Play returns to the foreground.
     *
     * Stremio Core intentionally keeps a cached authenticated profile for offline use, so a
     * session revoked from another Stremio client can otherwise look valid until an API-backed
     * profile refresh happens. We only force a logout for Stremio API error code 1 (invalid or
     * expired auth key). Network failures and other server errors are ignored so temporary
     * connectivity problems never sign the user out.
     */
    private fun validateRemoteSession() {
        if (sessionValidationInFlight) return

        val app = application as MainApplication
        val authRepository = app.container.authRepository
        val authKey = authRepository.getSavedAuthKey()
            ?.takeIf { it.isNotBlank() && it != "mock_auth_key" }
            ?: return

        sessionValidationInFlight = true
        lifecycleScope.launch {
            try {
                val revoked = withContext(Dispatchers.IO) {
                    isSessionRevoked(authKey)
                }
                if (revoked) {
                    Timber.i("Saved Stremio session was revoked remotely; clearing MW Play session")
                    authRepository.clearSavedSession()
                    app.container.core.logout()
                }
            } catch (error: Exception) {
                // Session validation is best-effort. Never log out because the network is down.
                Timber.w(error, "Unable to validate Stremio session")
            } finally {
                sessionValidationInFlight = false
            }
        }
    }

    private fun isSessionRevoked(authKey: String): Boolean {
        val connection = (URL("https://api.strem.io/api/getUser").openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val payload = JSONObject().put("authKey", authKey).toString()
            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                false
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val error = runCatching { JSONObject(response).optJSONObject("error") }.getOrNull()
                error?.optInt("code", -1) == 1
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
