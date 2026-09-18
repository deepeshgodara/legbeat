package com.legbeat.wear.data

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearCadenceListenerService : WearableListenerService() {

    @Inject
    lateinit var repository: WearCadenceRepository

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("WearCadenceService", "onMessageReceived: path=${messageEvent.path}, size=${messageEvent.data?.size}")
        if (messageEvent.path == CadencePacket.CADENCE_PATH) {
            val packet = CadencePacket.parse(messageEvent.data)
            Log.d("WearCadenceService", "Parsed packet: $packet")
            if (packet != null) {
                repository.onPacketReceived(packet)
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}
