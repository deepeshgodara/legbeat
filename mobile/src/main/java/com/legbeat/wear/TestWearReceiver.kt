package com.legbeat.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Diagnostic broadcast receiver for verifying Wear OS Bluetooth connectivity via ADB:
 * adb shell am broadcast -a com.legbeat.action.TEST_WEAR --ei rpm 88 --ei zone 1
 */
@AndroidEntryPoint
class TestWearReceiver : BroadcastReceiver() {

    @Inject
    lateinit var wearMessageSender: WearableMessageSender

    override fun onReceive(context: Context, intent: Intent) {
        val rpm = intent.getIntExtra("rpm", 88)
        val zone = intent.getIntExtra("zone", 1)
        android.util.Log.d("TestWearReceiver", "onReceive triggered with rpm=$rpm, zone=$zone")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = wearMessageSender.sendCadence(rpm, zone, System.currentTimeMillis())
                android.util.Log.d("TestWearReceiver", "sendCadence result: $res")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
