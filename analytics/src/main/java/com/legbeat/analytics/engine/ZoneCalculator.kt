package com.legbeat.analytics.engine

import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.CadenceZone

data class ZoneDuration(
    val zone: CadenceZone,
    val seconds: Long,
    val percentage: Float
)

data class ZoneBreakdown(
    val totalActiveSeconds: Long,
    val zones: List<ZoneDuration>
)

/**
 * Calculates Time-in-Zones from high-resolution cadence samples.
 */
class ZoneCalculator {

    fun calculate(samples: List<CadenceSample>): ZoneBreakdown {
        if (samples.isEmpty()) {
            val emptyZones = listOf(
                CadenceZone.RECOVERY,
                CadenceZone.ENDURANCE,
                CadenceZone.TEMPO,
                CadenceZone.HIGH_SPIN
            ).map { ZoneDuration(it, 0L, 0f) }
            return ZoneBreakdown(0L, emptyZones)
        }

        val zoneSecondsMap = mutableMapOf<CadenceZone, Long>().apply {
            put(CadenceZone.RECOVERY, 0L)
            put(CadenceZone.ENDURANCE, 0L)
            put(CadenceZone.TEMPO, 0L)
            put(CadenceZone.HIGH_SPIN, 0L)
        }

        var totalActiveSeconds = 0L

        for (i in samples.indices) {
            val current = samples[i]
            val zone = CadenceZone.fromRpm(current.rpm)

            // Calculate duration of this sample (difference to next sample, or 1 second default)
            val durationSec = if (i < samples.size - 1) {
                val diffMs = samples[i + 1].timestampMs - current.timestampMs
                (diffMs / 1000L).coerceIn(1L, 5L)
            } else {
                1L
            }

            if (zone != CadenceZone.IDLE) {
                zoneSecondsMap[zone] = (zoneSecondsMap[zone] ?: 0L) + durationSec
                totalActiveSeconds += durationSec
            }
        }

        val durations = listOf(
            CadenceZone.RECOVERY,
            CadenceZone.ENDURANCE,
            CadenceZone.TEMPO,
            CadenceZone.HIGH_SPIN
        ).map { zone ->
            val sec = zoneSecondsMap[zone] ?: 0L
            val pct = if (totalActiveSeconds > 0) (sec.toFloat() / totalActiveSeconds) * 100f else 0f
            ZoneDuration(zone, sec, pct)
        }

        return ZoneBreakdown(totalActiveSeconds, durations)
    }
}
