package com.stremio.mobile.data.settings

import com.stremio.core.models.StreamingServer
import org.json.JSONObject

/** Body of `POST <server>/settings`, using the field names the streaming server reads. */
internal fun serverSettingsPayload(settings: StreamingServer.Settings, seedingEnabled: Boolean): JSONObject =
    JSONObject().apply {
        put("appPath", settings.appPath)
        put("cacheRoot", settings.cacheRoot)
        put("serverVersion", settings.serverVersion)
        put("remoteHttps", settings.remoteHttps ?: JSONObject.NULL)
        put("transcodeProfile", settings.transcodeProfile ?: JSONObject.NULL)
        put("cacheSize", settings.cacheSize ?: JSONObject.NULL)
        put("proxyStreamsEnabled", settings.proxyStreamsEnabled)
        put("btMaxConnections", settings.btMaxConnections)
        put("btHandshakeTimeout", settings.btHandshakeTimeout)
        put("btRequestTimeout", settings.btRequestTimeout)
        put("btDownloadSpeedSoftLimit", settings.btDownloadSpeedSoftLimit)
        put("btDownloadSpeedHardLimit", settings.btDownloadSpeedHardLimit)
        put("btMinPeersForStable", settings.btMinPeersForStable)
        put("seedingEnabled", seedingEnabled)
    }
