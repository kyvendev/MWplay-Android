package com.stremio.mobile

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.stremio.mobile.auth.FacebookLoginBridge
import com.stremio.mobile.presentation.screens.player.PictureInPictureHost
import com.stremio.mobile.presentation.screens.player.PlayerPipRequest
import com.stremio.mobile.presentation.screens.player.supportsPlayerPip
import com.stremio.mobile.presentation.screens.StremioMobileApp
import com.stremio.mobile.presentation.viewmodel.MainViewModel
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

class MainActivity : FragmentActivity(), PictureInPictureHost {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    @Volatile private var sessionValidationInFlight = false
    private val viewModel: MainViewModel by viewModels {
        val app = application as MainApplication
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
                authRepository=app.container.authRepository,boardRepository=app.container.boardRepository,catalogRepository=app.container.catalogRepository,addonRepository=app.container.addonRepository,playbackRepository=app.container.playbackRepository,updateRepository=app.container.updateRepository,apkInstaller=app.container.apkInstaller,serverController=app.container.serverController,core=app.container.core,appContext=app.applicationContext
            ) as T
        }
    }
    override fun onCreate(savedInstanceState:Bundle?){val splash=installSplashScreen();splash.setKeepOnScreenCondition{viewModel.sessionRestoring.value};super.onCreate(savedInstanceState);WindowCompat.setDecorFitsSystemWindows(window,false);(application as MainApplication).castController.initialize();requestNotificationPermission();viewModel.acceptIntent(intent);setContent{StremioMobileApp(viewModel)}}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);viewModel.acceptIntent(intent)}
    @Suppress("DEPRECATION","OVERRIDE_DEPRECATION") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){if(FacebookLoginBridge.callbackManager.onActivityResult(requestCode,resultCode,data))return;super.onActivityResult(requestCode,resultCode,data)}
    override fun onStart(){super.onStart();viewModel.onAppForegrounded();validateRemoteSession()}
    override fun onStop(){super.onStop();viewModel.onAppBackgrounded()}

    // Set only while a local video is on screen (see PlayerPictureInPictureEffect).
    private var playerPip: PlayerPipRequest? = null

    override fun updatePlayerPip(request: PlayerPipRequest?) {
        playerPip = request
        if (supportsPlayerPip()) runCatching { setPictureInPictureParams(pipParams(request)) }.onFailure { Timber.w(it, "Unable to update PiP params") }
    }

    override fun enterPlayerPip(): Boolean {
        val request = playerPip ?: return false
        if (!supportsPlayerPip()) return false
        return runCatching { enterPictureInPictureMode(pipParams(request)) }.onFailure { Timber.w(it, "Unable to enter PiP") }.getOrDefault(false)
    }

    // Android 8-11 have no auto-enter: going Home while a video plays moves it into PiP here.
    // Android 12+ uses setAutoEnterEnabled instead, which also animates from the gesture.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && playerPip?.autoEnter == true) enterPlayerPip()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipParams(request: PlayerPipRequest?): PictureInPictureParams =
        PictureInPictureParams.Builder().apply {
            if (request != null) setAspectRatio(Rational(request.aspectWidth, request.aspectHeight))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(request?.autoEnter == true)
                setSeamlessResizeEnabled(true)
            }
        }.build()

    private fun validateRemoteSession(){
        if(sessionValidationInFlight)return
        val app=application as MainApplication; val repo=app.container.authRepository
        val key=repo.getSavedAuthKey()?.takeIf{it.isNotBlank()&&it!="mock_auth_key"}?:return
        sessionValidationInFlight=true
        lifecycleScope.launch { try { if(withContext(Dispatchers.IO){isSessionRevoked(key)}) { Timber.i("Saved Stremio session was revoked remotely; clearing MW Play credentials"); repo.clearSavedAuth(); app.container.core.logout() } } catch(e:Exception){Timber.w(e,"Unable to validate Stremio session")} finally {sessionValidationInFlight=false} }
    }

    /** Only definitive authentication failures revoke locally. Connectivity/server failures are ignored. */
    private fun isSessionRevoked(authKey:String):Boolean{
        val c=URL("https://api.strem.io/api/getUser").openConnection() as HttpURLConnection
        return try {
            c.requestMethod="POST";c.connectTimeout=5_000;c.readTimeout=5_000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json")
            c.outputStream.use{it.write(JSONObject().put("authKey",authKey).toString().toByteArray(Charsets.UTF_8))}
            val status=c.responseCode
            if(status==HttpURLConnection.HTTP_UNAUTHORIZED||status==HttpURLConnection.HTTP_FORBIDDEN) true
            else if(status==HttpURLConnection.HTTP_OK){val response=c.inputStream.bufferedReader().use{it.readText()};runCatching{JSONObject(response).optJSONObject("error")?.optInt("code",-1)==1}.getOrDefault(false)}
            else false
        } finally {c.disconnect()}
    }
    private fun requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}
}

