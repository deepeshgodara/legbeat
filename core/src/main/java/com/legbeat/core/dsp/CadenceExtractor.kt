package com.legbeat.core.dsp

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CadenceEstimate(
    val rpm: Int,
    val dominantFrequencyHz: Double,
    val confidence: Float,
    val isCoasting: Boolean
)

/**
 * Extracts pedaling cadence from the power spectrum.
 * Restricts search to human cycling band: 1.0 Hz (60 RPM) to 2.5 Hz (150 RPM).
 * Applies parabolic interpolation for sub-bin frequency accuracy and checks noise floor.
 */
class CadenceExtractor(
    val minFreqHz: Double = 1.0,
    val maxFreqHz: Double = 2.5,
    val noiseRmsThreshold: Double = 0.35,
    val minPeakRatio: Double = 0.18
) {
    /**
     * Extracts the estimated RPM from the power spectrum.
     * @param powerSpectrum Array of size N/2
     * @param sampleRate Target sampling rate (e.g. 50.0 Hz)
     * @param fftSize FFT points (e.g. 256)
     * @param rawRms Root-mean-square of windowed signal minus mean
     */
    fun extract(
        powerSpectrum: DoubleArray,
        sampleRate: Double,
        fftSize: Int,
        rawRms: Double
    ): CadenceEstimate {
        // Coasting / stopped check based on acceleration energy
        if (rawRms < noiseRmsThreshold) {
            return CadenceEstimate(
                rpm = 0,
                dominantFrequencyHz = 0.0,
                confidence = 0f,
                isCoasting = true
            )
        }

        val freqResolution = sampleRate / fftSize
        val minBin = ceil(minFreqHz / freqResolution).toInt().coerceAtLeast(1)
        val maxBin = floor(maxFreqHz / freqResolution).toInt().coerceAtMost(powerSpectrum.size - 2)

        if (minBin >= maxBin) {
            return CadenceEstimate(0, 0.0, 0f, true)
        }

        var peakBin = minBin
        var peakPower = powerSpectrum[minBin]
        var totalBandPower = 0.0

        for (k in minBin..maxBin) {
            val p = powerSpectrum[k]
            totalBandPower += p
            if (p > peakPower) {
                peakPower = p
                peakBin = k
            }
        }

        // Spectral purity check: dominant peak must stand out from noise floor
        val peakRatio = if (totalBandPower > 0) peakPower / totalBandPower else 0.0
        if (peakRatio < minPeakRatio) {
            return CadenceEstimate(0, 0.0, 0f, true)
        }

        // Parabolic interpolation around peak bin to refine frequency
        val alpha = powerSpectrum[peakBin - 1]
        val beta = powerSpectrum[peakBin]
        val gamma = powerSpectrum[peakBin + 1]

        val denom = alpha - 2.0 * beta + gamma
        val delta = if (denom != 0.0) {
            0.5 * (alpha - gamma) / denom
        } else {
            0.0
        }.coerceIn(-0.5, 0.5)

        val refinedBin = peakBin + delta
        val dominantFreqHz = refinedBin * freqResolution
        val rawRpm = (dominantFreqHz * 60.0).roundToInt().coerceIn(60, 160)
        val confidence = peakRatio.toFloat().coerceIn(0f, 1f)

        return CadenceEstimate(
            rpm = rawRpm,
            dominantFrequencyHz = dominantFreqHz,
            confidence = confidence,
            isCoasting = false
        )
    }

    /**
     * Helper to compute RMS of samples centered around their mean.
     */
    fun computeCenteredRms(samples: DoubleArray): Double {
        if (samples.isEmpty()) return 0.0
        val mean = samples.average()
        var sumSquares = 0.0
        for (v in samples) {
            val diff = v - mean
            sumSquares += diff * diff
        }
        return sqrt(sumSquares / samples.size)
    }
}
