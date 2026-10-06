package com.stremio.mobile.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.data.model.LIQUID_GLASS_RECOMMENDED_GLOBAL_ALPHA
import com.stremio.mobile.data.model.LocalStreamSelection
import com.stremio.mobile.data.model.LiquidGlassTuning
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.core.theme.AppFont
import com.stremio.mobile.presentation.state.AccountUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class AuthRepository(private val core: StremioCore, private val context: Context) {
    private val preferences = context.getSharedPreferences("stremio_account", Context.MODE_PRIVATE)
    fun getSelectedFont(): AppFont = runCatching { AppFont.valueOf(preferences.getString("selected_font", AppFont.PLUS_JAKARTA_SANS.name) ?: AppFont.PLUS_JAKARTA_SANS.name) }.getOrDefault(AppFont.PLUS_JAKARTA_SANS)
    fun setSelectedFont(font: AppFont) { preferences.edit().putString("selected_font", font.name).apply() }
    fun getAccountStateFlow(): Flow<AccountUiState> = core.ctx().map { ctx -> ctx.profile.auth?.let { AccountUiState(isAuthenticated=true,email=it.user.email,authKey=it.key) } ?: AccountUiState() }
    fun getProfileSettingsFlow() = core.ctx().map { it.profile.settings }
    fun getTraktAuthFlow() = core.ctx().map { ctx -> ctx.profile.auth?.user?.trakt?.let { (System.currentTimeMillis()/1000) < (it.createdAt.seconds + it.expiresIn.seconds) } ?: false }
    fun accountFromCore(): AccountUiState = runCatching { core.getCtx().profile.auth }.getOrNull()?.let { AccountUiState(isAuthenticated=true,email=it.user.email,authKey=it.key) } ?: AccountUiState()
    fun isAutoStartOnBoot()=preferences.getBoolean("auto_start_on_boot",false); fun setAutoStartOnBoot(v:Boolean){preferences.edit().putBoolean("auto_start_on_boot",v).apply()}
    fun isServerInForeground()=preferences.getBoolean("server_in_foreground",true); fun setServerInForeground(v:Boolean){preferences.edit().putBoolean("server_in_foreground",v).commit()}
    fun isMobileDataWarning()=preferences.getBoolean("mobile_data_warning",true); fun setMobileDataWarning(v:Boolean){preferences.edit().putBoolean("mobile_data_warning",v).apply()}
    fun isKeepScreenOn()=preferences.getBoolean("keep_screen_on",true); fun setKeepScreenOn(v:Boolean){preferences.edit().putBoolean("keep_screen_on",v).apply()}
    fun isAnalyticsEnabled()=preferences.getBoolean("analytics_enabled",true); fun setAnalyticsEnabled(v:Boolean){preferences.edit().putBoolean("analytics_enabled",v).apply()}
    fun isAnalyticsDisclosureAcknowledged()=preferences.getBoolean("analytics_disclosure_acknowledged",false); fun setAnalyticsDisclosureAcknowledged(v:Boolean){preferences.edit().putBoolean("analytics_disclosure_acknowledged",v).apply()}
    fun isAutoUpdateEnabled()=preferences.getBoolean("auto_update_enabled",true); fun setAutoUpdateEnabled(v:Boolean){preferences.edit().putBoolean("auto_update_enabled",v).apply()}
    fun getLastUpdateCheckMs()=preferences.getLong("last_update_check_ms",0L); fun setLastUpdateCheckMs(v:Long){preferences.edit().putLong("last_update_check_ms",v).apply()}
    fun getIgnoredUpdateVersion():String?=preferences.getString("ignored_update_version",null); fun setIgnoredUpdateVersion(v:String?){preferences.edit().putString("ignored_update_version",v).apply()}
    fun getMinSeedsThreshold()=preferences.getInt("min_seeds_threshold",1); fun setMinSeedsThreshold(v:Int){preferences.edit().putInt("min_seeds_threshold",v).apply()}
    fun getMinDownloadSpeedBps()=preferences.getLong("min_download_speed_bps",50_000L); fun setMinDownloadSpeedBps(v:Long){preferences.edit().putLong("min_download_speed_bps",v).apply()}
    fun getPreferredQuality()=preferences.getString("preferred_quality","Any")?:"Any"; fun setPreferredQuality(v:String){preferences.edit().putString("preferred_quality",v).apply()}
    fun rememberLocalStreamSelection(itemType:String,itemId:String,videoId:String?,option:StreamOption){val p=JSONObject().put("key",option.key).put("addonTitle",option.addonTitle).put("name",option.name).put("description",option.description).put("quality",option.quality);preferences.edit().putString(localStreamSelectionKey(itemType,itemId,videoId),p.toString()).apply()}
    fun getLocalStreamSelection(itemType:String,itemId:String,videoId:String?):LocalStreamSelection?{val raw=preferences.getString(localStreamSelectionKey(itemType,itemId,videoId),null)?:return null;return runCatching{val p=JSONObject(raw);LocalStreamSelection(p.optString("key"),p.optString("addonTitle"),p.optString("name"),p.optNullableString("description"),p.optNullableString("quality"))}.getOrNull()}
    private fun localStreamSelectionKey(itemType:String,itemId:String,videoId:String?)=listOf("local_stream_selection",itemType,itemId,videoId?.takeIf{it.isNotBlank()}?:itemId).joinToString(":"){Uri.encode(it)}
    private fun JSONObject.optNullableString(name:String):String?=if(has(name)&&!isNull(name))optString(name) else null
    fun getGlobalUiStyle():String=when(preferences.getString("global_ui_style",null)?:preferences.getString("player_controls_style",null)){"modern"->"modern";else->"classic"}; fun setGlobalUiStyle(v:String){preferences.edit().putString("global_ui_style",if(v=="modern")"modern" else "classic").apply()}
    fun getPlayerUiStyle()=preferences.getString("player_ui_style","global")?:"global"; fun setPlayerUiStyle(v:String){preferences.edit().putString("player_ui_style",when(v){"classic"->"classic";"modern"->"modern";else->"global"}).apply()}
    fun getGlassEffectsMode()=when(preferences.getString("glass_effects_mode","balanced")){"full"->"full";"static"->"static";else->"balanced"}; fun setGlassEffectsMode(v:String){preferences.edit().putString("glass_effects_mode",when(v){"full"->"full";"static"->"static";else->"balanced"}).apply()}
    fun getGlobalGlassAlpha()=preferences.getFloat("global_glass_alpha",LIQUID_GLASS_RECOMMENDED_GLOBAL_ALPHA); fun setGlobalGlassAlpha(v:Float){preferences.edit().putFloat("global_glass_alpha",v).apply()}
    fun getGlassHapticsEnabled()=preferences.getBoolean("glass_haptics_enabled",true); fun setGlassHapticsEnabled(v:Boolean){preferences.edit().putBoolean("glass_haptics_enabled",v).apply()}
    fun isAdaptiveGlassContrastEnabled()=preferences.getBoolean("adaptive_glass_contrast",true); fun setAdaptiveGlassContrastEnabled(v:Boolean){preferences.edit().putBoolean("adaptive_glass_contrast",v).apply()}
    fun getHapticsIntensity()=preferences.getString("haptics_intensity","Medium")?:"Medium"; fun setHapticsIntensity(v:String){preferences.edit().putString("haptics_intensity",v).apply()}
    fun getLiquidGlassTuning():LiquidGlassTuning{val d=LiquidGlassTuning();return LiquidGlassTuning(preferences.getFloat("glass_tuning_blur_dp",d.blurDp),preferences.getFloat("glass_tuning_refraction_height_dp",d.refractionHeightDp),preferences.getFloat("glass_tuning_refraction_amount_dp",d.refractionAmountDp),preferences.getFloat("glass_tuning_surface_alpha",d.surfaceAlpha),preferences.getFloat("glass_tuning_highlight_alpha",d.highlightAlpha),preferences.getFloat("glass_tuning_border_alpha",d.borderAlpha),preferences.getFloat("glass_tuning_shadow_alpha",d.shadowAlpha),preferences.getFloat("glass_tuning_track_alpha",d.trackAlpha),preferences.getFloat("glass_tuning_thumb_alpha",d.thumbAlpha),preferences.getBoolean("glass_tuning_chromatic_aberration",d.chromaticAberration)).clamped()}
    fun setLiquidGlassTuning(v:LiquidGlassTuning){val t=v.clamped();preferences.edit().putFloat("glass_tuning_blur_dp",t.blurDp).putFloat("glass_tuning_refraction_height_dp",t.refractionHeightDp).putFloat("glass_tuning_refraction_amount_dp",t.refractionAmountDp).putFloat("glass_tuning_surface_alpha",t.surfaceAlpha).putFloat("glass_tuning_highlight_alpha",t.highlightAlpha).putFloat("glass_tuning_border_alpha",t.borderAlpha).putFloat("glass_tuning_shadow_alpha",t.shadowAlpha).putFloat("glass_tuning_track_alpha",t.trackAlpha).putFloat("glass_tuning_thumb_alpha",t.thumbAlpha).putBoolean("glass_tuning_chromatic_aberration",t.chromaticAberration).apply()}
    fun isAutoSwitchOnDeadStream()=preferences.getBoolean("auto_switch_on_dead_stream",false); fun setAutoSwitchOnDeadStream(v:Boolean){preferences.edit().putBoolean("auto_switch_on_dead_stream",v).apply()}
    fun isTraktAuthenticated():Boolean{val t=runCatching{core.getCtx().profile.auth?.user?.trakt}.getOrNull()?:return false;return System.currentTimeMillis()/1000 < t.createdAt.seconds+t.expiresIn.seconds}
    fun authenticateTrakt(context:Context){val u=runCatching{core.getCtx().profile.auth?.user}.getOrNull()?:return;context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.strem.io/trakt/auth/${u.id}")))}
    suspend fun logoutTrakt(){core.logoutTrakt()}; suspend fun installTraktAddon(){core.installTraktAddon()}
    fun getSavedAuthKey():String?=preferences.getString("authKey",null)
    fun saveAuthKeyAndEmail(authKey:String,email:String){preferences.edit().putString("authKey",authKey).putString("email",email).apply()}
    /** Removes only credentials. App/UI/server preferences survive a remotely revoked session. */
    fun clearSavedAuth(){preferences.edit().remove("authKey").remove("email").apply()}
    /** Full reset retained for flows that intentionally need to wipe all local account preferences. */
    fun clearSavedSession(){preferences.edit().clear().apply()}
    suspend fun loginWithToken(authKey:String){core.loginWithToken(authKey)}; suspend fun loginWithFacebook(token:String){core.loginWithFacebook(token)}; suspend fun login(email:String,password:String){core.login(email.trim(),password)}; suspend fun register(email:String,password:String,marketingConsent:Boolean){core.register(email.trim(),password,marketingConsent)}; suspend fun logout(){core.logout()}; fun isAuthenticated()=core.isAuthenticated(); suspend fun updateSettings(newSettings:com.stremio.core.types.profile.Profile.Settings){core.updateSettings(newSettings)}
}
