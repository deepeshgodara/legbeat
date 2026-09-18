package com.legbeat.wear

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WatchDeviceInfo(
    val id: String,
    val name: String,
    val isNearby: Boolean
)

/**
 * Streams real-time cadence RPM and zone updates to paired Wear OS devices
 * using the Google Play Services Wearable MessageClient and CapabilityClient.
 */
class WearableMessageSender(
    private val context: Context
) {
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }
    private val capabilityClient: CapabilityClient by lazy { Wearable.getCapabilityClient(context) }

    suspend fun getConnectedWatches(): List<WatchDeviceInfo> = runCatching {
        val nodes = nodeClient.connectedNodes.await()
        android.util.Log.d("WearableSender", "getConnectedWatches: found ${nodes.size} nodes: ${nodes.map { it.displayName }}")
        nodes.map { WatchDeviceInfo(it.id, it.displayName, it.isNearby) }
    }.getOrElse {
        android.util.Log.e("WearableSender", "getConnectedWatches error", it)
        emptyList()
    }

    suspend fun sendCadence(rpm: Int, zoneCode: Int, timestampMs: Long): Result<Int> = runCatching {
        // First attempt to locate nodes advertising the cadence display capability
        val capabilityNodes = try {
            val cap = capabilityClient.getCapability(CAPABILITY_CADENCE_DISPLAY, CapabilityClient.FILTER_REACHABLE).await()
            android.util.Log.d("WearableSender", "Capability nodes: ${cap.nodes.size} nodes: ${cap.nodes.map { it.displayName }}")
            cap.nodes
        } catch (e: Exception) {
            android.util.Log.e("WearableSender", "Capability query failed", e)
            emptySet()
        }

        // Fall back to all connected reachable nodes
        val targetNodes = if (capabilityNodes.isNotEmpty()) {
            capabilityNodes
        } else {
            val allNodes = nodeClient.connectedNodes.await().toSet()
            android.util.Log.d("WearableSender", "Fallback all connected nodes: ${allNodes.size} nodes: ${allNodes.map { it.displayName }}")
            allNodes
        }

        if (targetNodes.isEmpty()) {
            android.util.Log.w("WearableSender", "No target nodes found to send cadence!")
            return@runCatching 0
        }

        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(rpm)
        buffer.putInt(zoneCode)
        buffer.putLong(timestampMs)
        val payload = buffer.array()

        var successCount = 0
        for (node in targetNodes) {
            try {
                messageClient.sendMessage(node.id, CADENCE_PATH, payload).await()
                android.util.Log.d("WearableSender", "Sent cadence $rpm RPM to ${node.displayName} (${node.id})")
                successCount++
            } catch (e: Exception) {
                android.util.Log.e("WearableSender", "Failed sending to node ${node.displayName}", e)
            }
        }
        successCount
    }

    suspend fun sendTestCadence(rpm: Int = 88): Result<Int> {
        return sendCadence(rpm, 1, System.currentTimeMillis())
    }

    suspend fun openAppOnWatch(): Result<Int> = runCatching {
        val capabilityNodes = try {
            val cap = capabilityClient.getCapability(CAPABILITY_CADENCE_DISPLAY, CapabilityClient.FILTER_REACHABLE).await()
            cap.nodes
        } catch (_: Exception) {
            emptySet()
        }

        val targetNodes = if (capabilityNodes.isNotEmpty()) {
            capabilityNodes
        } else {
            nodeClient.connectedNodes.await().toSet()
        }

        if (targetNodes.isEmpty()) {
            android.util.Log.w("WearableSender", "openAppOnWatch: No target watch nodes found!")
            return@runCatching 0
        }

        var successCount = 0
        for (node in targetNodes) {
            try {
                messageClient.sendMessage(node.id, OPEN_APP_PATH, ByteArray(0)).await()
                android.util.Log.d("WearableSender", "Sent open app command to ${node.displayName}")
                successCount++
            } catch (e: Exception) {
                android.util.Log.e("WearableSender", "Failed sending open app command to ${node.displayName}", e)
            }
        }
        successCount
    }

    companion object {
        const val CADENCE_PATH = "/legbeat/cadence"
        const val OPEN_APP_PATH = "/legbeat/open_app"
        const val CAPABILITY_CADENCE_DISPLAY = "legbeat_cadence_display"
    }
}
