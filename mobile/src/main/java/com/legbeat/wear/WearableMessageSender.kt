package com.legbeat.wear

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams real-time cadence RPM and zone updates to paired Wear OS devices
 * using the Google Play Services Wearable MessageClient.
 */
class WearableMessageSender(
    private val context: Context
) {
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun sendCadence(rpm: Int, zoneCode: Int, timestampMs: Long): Result<Int> = runCatching {
        val nodes = nodeClient.connectedNodes.await()
        if (nodes.isEmpty()) return@runCatching 0

        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(rpm)
        buffer.putInt(zoneCode)
        buffer.putLong(timestampMs)
        val payload = buffer.array()

        var successCount = 0
        for (node in nodes) {
            try {
                messageClient.sendMessage(node.id, CADENCE_PATH, payload).await()
                successCount++
            } catch (ignored: Exception) {
                // Ignore transient node disconnect
            }
        }
        successCount
    }

    companion object {
        const val CADENCE_PATH = "/legbeat/cadence"
    }
}
