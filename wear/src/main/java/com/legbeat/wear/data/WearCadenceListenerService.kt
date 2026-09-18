package com.legbeat.wear.data

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.legbeat.wear.presentation.WearCadenceActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearCadenceListenerService : WearableListenerService() {

    @Inject
    lateinit var repository: WearCadenceRepository

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("WearCadenceService", "onMessageReceived: path=${messageEvent.path}, size=${messageEvent.data?.size}")
        when (messageEvent.path) {
            OPEN_APP_PATH -> {
                Log.d("WearCadenceService", "Received OPEN_APP command, launching WearCadenceActivity")
                val launchIntent = Intent(this, WearCadenceActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launchIntent)
            }
            CadencePacket.CADENCE_PATH -> {
                val packet = CadencePacket.parse(messageEvent.data)
                Log.d("WearCadenceService", "Parsed packet: $packet")
                if (packet != null) {
                    repository.onPacketReceived(packet)
                }
            }
            else -> {
                super.onMessageReceived(messageEvent)
            }
        }
    }

    companion object {
        const val OPEN_APP_PATH = "/legbeat/open_app"
    }
}
