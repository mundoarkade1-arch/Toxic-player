package com.meuapp.player.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Observa a saída de áudio (alto-falante, fones com fio, Bluetooth) e avisa o [AudioFx]. */
object RouteWatcher {
    private var callback: AudioDeviceCallback? = null

    fun start(ctx: Context) {
        if (callback != null) return
        val am = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { evaluate(am) }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { evaluate(am) }
        }
        am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        callback = cb
        evaluate(am)
    }

    private fun evaluate(am: AudioManager) {
        val devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = devs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val wired = devs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        AudioFx.routeChanged(if (bt) "bluetooth" else if (wired) "wired" else "speaker")
    }
}
