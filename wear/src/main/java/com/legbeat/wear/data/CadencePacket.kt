package com.legbeat.wear.data

import com.legbeat.core.model.CadenceZone
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CadencePacket(
    val rpm: Int,
    val zone: CadenceZone,
    val timestampMs: Long
) {
    companion object {
        const val CADENCE_PATH = "/legbeat/cadence"

        fun parse(bytes: ByteArray): CadencePacket? {
            if (bytes.size < 16) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val rpm = buffer.int
            val zoneCode = buffer.int
            val timestamp = buffer.long
            return CadencePacket(
                rpm = rpm,
                zone = CadenceZone.fromCode(zoneCode),
                timestampMs = timestamp
            )
        }
    }
}
