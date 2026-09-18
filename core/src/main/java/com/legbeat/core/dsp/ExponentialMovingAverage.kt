package com.legbeat.core.dsp

import kotlin.math.roundToInt

/**
 * Exponential Moving Average (EMA) filter for smoothing calculated cadence values.
 * Default alpha = 0.35 provides rapid responsiveness to cadence changes while
 * dampening momentary frame vibrations and pedal stroke surges.
 */
class ExponentialMovingAverage(
    val alpha: Double = 0.35
) {
    private var smoothedValue: Double? = null

    fun update(newValue: Int): Int {
        if (newValue <= 0) {
            // Instant drop to 0 if coasting is detected for fast feedback
            smoothedValue = 0.0
            return 0
        }

        val current = smoothedValue
        val updated = if (current == null || current <= 0.0) {
            newValue.toDouble()
        } else {
            alpha * newValue + (1.0 - alpha) * current
        }
        smoothedValue = updated
        return updated.roundToInt()
    }

    fun reset() {
        smoothedValue = null
    }
}
