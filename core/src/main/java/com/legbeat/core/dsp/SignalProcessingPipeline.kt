package com.legbeat.core.dsp

/**
 * High-level coordinator orchestrating the rolling window buffer, resampling,
 * Hann windowing, Cooley-Tukey FFT, peak detection, and exponential smoothing.
 */
class SignalProcessingPipeline(
    val buffer: RollingWindowBuffer = RollingWindowBuffer(),
    val resampler: SignalResampler = SignalResampler(targetSampleRateHz = 50.0),
    val window: HannWindow = HannWindow(),
    val fft: CooleyTukeyFft = CooleyTukeyFft(),
    val extractor: CadenceExtractor = CadenceExtractor(),
    val smoother: ExponentialMovingAverage = ExponentialMovingAverage()
) {
    companion object {
        const val RESAMPLED_COUNT = 150 // 3.0 seconds @ 50 Hz
        const val FFT_SIZE = 256        // Next power of 2 for zero-padding
    }

    /**
     * Ingest an incoming TYPE_LINEAR_ACCELERATION sensor reading.
     */
    fun onSensorSample(timestampNs: Long, x: Float, y: Float, z: Float) {
        buffer.addSample(timestampNs, x, y, z)
    }

    /**
     * Analyzes the current 3-second window and returns the estimated cadence.
     */
    fun processWindow(): CadenceEstimate {
        val snapshot = buffer.getSnapshot()
        // Need at least 20 samples to estimate frequency reliably
        if (snapshot.size < 20) {
            return CadenceEstimate(0, 0.0, 0f, true)
        }

        // 1. Resample to uniform 50 Hz
        val uniform = resampler.resample(snapshot, RESAMPLED_COUNT)

        // 2. Measure RMS for noise/coasting detection
        val rms = extractor.computeCenteredRms(uniform)

        // 3. Apply Hann window to remove boundary discontinuities
        val windowed = window.apply(uniform)

        // 4. Zero-pad to 256 points for FFT
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        System.arraycopy(windowed, 0, real, 0, windowed.size)

        // 5. Compute Radix-2 FFT and power spectrum
        fft.transform(real, imag)
        val powerSpectrum = fft.computePowerSpectrum(real, imag)

        // 6. Extract dominant frequency in 1.0 Hz - 2.5 Hz (60 - 150 RPM)
        val estimate = extractor.extract(
            powerSpectrum = powerSpectrum,
            sampleRate = resampler.targetSampleRateHz,
            fftSize = FFT_SIZE,
            rawRms = rms
        )

        // 7. Apply smoothing
        val smoothedRpm = smoother.update(estimate.rpm)

        return estimate.copy(rpm = smoothedRpm)
    }

    fun setSensitivity(percent: Int) {
        extractor.setSensitivity(percent)
    }

    fun reset() {
        buffer.clear()
        smoother.reset()
    }
}
