package com.legbeat.wear.data

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearCadenceListenerService : WearableListenerService() {

    @Inject
    lateinit var repository: WearCadenceRepository

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == CadencePacket.CADENCE_PATH) {
            val packet = CadencePacket.parse(messageEvent.data)
            if (packet != null) {
                repository.onPacketReceived(packet)
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}
