package com.stremio.mobile.player.session

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AudioOutputType { SPEAKER, WIRED, BLUETOOTH, USB, HEARING_AID, HDMI, OTHER }

/**
 * Where media audio is currently routed. Uses AudioDeviceCallback (no permission, no polling):
 * the system reports every output added or removed and the route is re-derived from the
 * connected outputs.
 */
class AudioOutputMonitor(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mutableOutput = MutableStateFlow(AudioOutputType.SPEAKER)
    val output: StateFlow<AudioOutputType> = mutableOutput
    private var registered = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    fun start() {
        if (registered) return
        registered = true
        // The callback also fires once with the current devices right after registration.
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        if (!registered) return
        registered = false
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private fun refresh() {
        mutableOutput.value = mediaOutputFor(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type })
    }
}

/**
 * Media follows the external output the user connected, so a private output (Bluetooth, hearing
 * aid, wired or USB headset) wins over HDMI/docks, which win over the built-in speaker. Bluetooth
 * SCO alone is a call-only link and does not carry media.
 */
internal fun mediaOutputFor(deviceTypes: List<Int>): AudioOutputType {
    fun has(vararg types: Int) = deviceTypes.any { it in types }
    return when {
        has(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST) -> AudioOutputType.BLUETOOTH
        has(AudioDeviceInfo.TYPE_HEARING_AID) -> AudioOutputType.HEARING_AID
        has(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES) -> AudioOutputType.WIRED
        has(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY) -> AudioOutputType.USB
        has(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC) -> AudioOutputType.HDMI
        has(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) -> AudioOutputType.SPEAKER
        deviceTypes.isEmpty() -> AudioOutputType.SPEAKER
        else -> AudioOutputType.OTHER
    }
}

/** Outputs only the user hears; losing one of them must not blast audio out of the speaker. */
internal val AudioOutputType.isPrivate: Boolean
    get() = this == AudioOutputType.BLUETOOTH || this == AudioOutputType.HEARING_AID ||
        this == AudioOutputType.WIRED || this == AudioOutputType.USB
