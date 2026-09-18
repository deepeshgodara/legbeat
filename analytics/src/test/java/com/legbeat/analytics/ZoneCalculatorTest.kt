package com.legbeat.analytics

import com.legbeat.analytics.engine.OfflineCoachingEngine
import com.legbeat.analytics.engine.ZoneCalculator
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.CadenceZone
import com.legbeat.core.model.Ride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneCalculatorTest {

    @Test
    fun `calculates Time in Zones correctly across samples`() {
        val calculator = ZoneCalculator()
        val baseTime = 1700000000000L

        // 30 seconds at 60 RPM (Recovery)
        // 40 seconds at 80 RPM (Endurance)
        // 30 seconds at 95 RPM (Tempo)
        val samples = mutableListOf<CadenceSample>()
        for (i in 0 until 30) {
            samples.add(CadenceSample(baseTime + i * 1000L, 60))
        }
        for (i in 30 until 70) {
            samples.add(CadenceSample(baseTime + i * 1000L, 80))
        }
        for (i in 70 until 100) {
            samples.add(CadenceSample(baseTime + i * 1000L, 95))
        }

        val breakdown = calculator.calculate(samples)
        assertEquals(100L, breakdown.totalActiveSeconds)

        val recoveryZone = breakdown.zones.first { it.zone == CadenceZone.RECOVERY }
        val enduranceZone = breakdown.zones.first { it.zone == CadenceZone.ENDURANCE }
        val tempoZone = breakdown.zones.first { it.zone == CadenceZone.TEMPO }

        assertEquals(30f, recoveryZone.percentage, 1.0f)
        assertEquals(40f, enduranceZone.percentage, 1.0f)
        assertEquals(30f, tempoZone.percentage, 1.0f)
    }

    @Test
    fun `offline coaching engine flags grinding when recovery percentage is excessive`() {
        val engine = OfflineCoachingEngine()
        val baseTime = 1700000000000L
        val samples = (0 until 60).map { i ->
            CadenceSample(baseTime + i * 1000L, 55) // Low RPM grinding
        }
        val ride = Ride(
            id = "test_grind",
            startTimeMs = baseTime,
            endTimeMs = baseTime + 60000L,
            avgCadence = 55,
            maxCadence = 62,
            durationMs = 60000L
        )

        val insights = engine.generateInsights(ride, samples)
        assertTrue("Should detect grinding", insights.any { it.title.contains("Grinding", ignoreCase = true) })
    }
}
