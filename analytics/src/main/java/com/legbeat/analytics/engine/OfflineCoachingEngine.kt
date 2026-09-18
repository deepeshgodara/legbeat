package com.legbeat.analytics.engine

import com.legbeat.analytics.db.dao.CadenceWithHeartRate
import com.legbeat.core.model.CadenceSample
import com.legbeat.core.model.CadenceZone
import com.legbeat.core.model.Ride

data class CoachingInsight(
    val title: String,
    val description: String,
    val recommendation: String,
    val efficiencyScore: Int // 0 - 100
)

/**
 * On-Device Offline Coaching Engine.
 * Evaluates pedaling cadence metrics and cardiovascular strain without any cloud connectivity.
 */
class OfflineCoachingEngine(
    private val zoneCalculator: ZoneCalculator = ZoneCalculator()
) {
    fun generateInsights(
        ride: Ride,
        samples: List<CadenceSample>,
        joinedMetrics: List<CadenceWithHeartRate> = emptyList()
    ): List<CoachingInsight> {
        val insights = mutableListOf<CoachingInsight>()
        val breakdown = zoneCalculator.calculate(samples)

        val enduranceZone = breakdown.zones.firstOrNull { it.zone == CadenceZone.ENDURANCE }
        val recoveryZone = breakdown.zones.firstOrNull { it.zone == CadenceZone.RECOVERY }
        val tempoZone = breakdown.zones.firstOrNull { it.zone == CadenceZone.TEMPO }
        val highSpinZone = breakdown.zones.firstOrNull { it.zone == CadenceZone.HIGH_SPIN }

        val endurancePct = enduranceZone?.percentage ?: 0f
        val recoveryPct = recoveryZone?.percentage ?: 0f
        val tempoPct = tempoZone?.percentage ?: 0f

        // 1. Cadence Distribution & Efficiency Analysis
        if (endurancePct + tempoPct >= 65f) {
            insights.add(
                CoachingInsight(
                    title = "Optimal Aerobic Cadence",
                    description = "You maintained an optimal pedaling cadence (${(endurancePct + tempoPct).toInt()}% in 70-100 RPM) throughout the session.",
                    recommendation = "Maintaining 80-95 RPM spares knee cartilage and shifts mechanical strain to your aerobic system.",
                    efficiencyScore = 92
                )
            )
        } else if (recoveryPct > 40f) {
            insights.add(
                CoachingInsight(
                    title = "High Gear Grinding Detected",
                    description = "${recoveryPct.toInt()}% of your active pedaling was below 70 RPM.",
                    recommendation = "Shift to an easier gear and increase cadence towards 85 RPM to reduce muscular fatigue and joint pressure.",
                    efficiencyScore = 58
                )
            )
        }

        // 2. Cross-Metric Cardiovascular Strain Analysis (when Heart Rate is paired)
        val hrSamples = joinedMetrics.filter { it.bpm != null && it.bpm > 0 }
        if (hrSamples.isNotEmpty()) {
            val grindingWithHighHr = hrSamples.count { it.rpm < 75 && it.bpm!! > 150 }
            val spinningWithHighHr = hrSamples.count { it.rpm > 95 && it.bpm!! > 150 }

            if (grindingWithHighHr > hrSamples.size * 0.25) {
                insights.add(
                    CoachingInsight(
                        title = "Cardiovascular Strain vs High Gear Resistance",
                        description = "Elevated heart rate detected during low-cadence (<75 RPM) segments.",
                        recommendation = "High muscular resistance is driving your cardiovascular strain. Try spinning 10-15 RPM faster on climbs.",
                        efficiencyScore = 65
                    )
                )
            } else if (spinningWithHighHr > hrSamples.size * 0.25) {
                insights.add(
                    CoachingInsight(
                        title = "Aerobic Cardiovascular Utilization",
                        description = "Smooth high-cadence pedaling sustained under intense aerobic exertion.",
                        recommendation = "Excellent neuromuscular economy. Your cadence facilitates rapid lactate clearance.",
                        efficiencyScore = 95
                    )
                )
            }
        }

        if (insights.isEmpty()) {
            insights.add(
                CoachingInsight(
                    title = "Consistent Pedaling Rhythm",
                    description = "Average cadence recorded was ${ride.avgCadence} RPM over ${ride.durationMs / 60000} minutes.",
                    recommendation = "Aim for a target sweet spot of 85-90 RPM for sustained endurance riding.",
                    efficiencyScore = 80
                )
            )
        }

        return insights
    }
}
