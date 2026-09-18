package com.legbeat.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SignalProcessingPipelineTest {

    @Test
    fun `detects 90 RPM cadence from 1_5 Hz periodic acceleration`() {
        val pipeline = SignalProcessingPipeline()
        val freqHz = 1.5 // 1.5 Hz * 60 = 90 RPM
        val sampleIntervalNs = 20_000_000L // 50 Hz = 20 ms
        val totalSamples = 150 // 3.0 seconds

        for (i in 0 until totalSamples) {
            val tSec = (i * sampleIntervalNs) / 1_000_000_000.0
            // Leg motion acceleration amplitude ~2.0 m/s^2
            val accel = (2.0 * sin(2.0 * PI * freqHz * tSec)).toFloat()
            val timestampNs = i * sampleIntervalNs
            pipeline.onSensorSample(timestampNs, 0f, accel, 0f)
        }

        val estimate = pipeline.processWindow()
        assertFalse("Should not be coasting with active pedaling", estimate.isCoasting)
        assertEquals(90.0, estimate.rpm.toDouble(), 3.0) // Within +-3 RPM accuracy
    }

    @Test
    fun `detects 120 RPM cadence from 2_0 Hz periodic acceleration`() {
        val pipeline = SignalProcessingPipeline()
        val freqHz = 2.0 // 2.0 Hz * 60 = 120 RPM
        val sampleIntervalNs = 20_000_000L
        val totalSamples = 150

        for (i in 0 until totalSamples) {
            val tSec = (i * sampleIntervalNs) / 1_000_000_000.0
            val accel = (2.5 * sin(2.0 * PI * freqHz * tSec)).toFloat()
            val timestampNs = i * sampleIntervalNs
            pipeline.onSensorSample(timestampNs, accel, 0f, 0f)
        }

        val estimate = pipeline.processWindow()
        assertFalse("Should not be coasting", estimate.isCoasting)
        assertEquals(120.0, estimate.rpm.toDouble(), 3.0)
    }

    @Test
    fun `outputs 0 RPM when movement is below noise threshold (coasting)`() {
        val pipeline = SignalProcessingPipeline()
        val sampleIntervalNs = 20_000_000L
        val totalSamples = 150

        // Tiny acceleration noise (RMS ~0.05 m/s^2)
        for (i in 0 until totalSamples) {
            val accel = (0.05 * sin(i.toDouble())).toFloat()
            pipeline.onSensorSample(i * sampleIntervalNs, accel, 0f, 0f)
        }

        val estimate = pipeline.processWindow()
        assertTrue("Must be marked as coasting", estimate.isCoasting)
        assertEquals(0, estimate.rpm)
    }

    @Test
    fun `HannWindow weights sum to less than rectangular window`() {
        val window = HannWindow()
        val input = DoubleArray(100) { 1.0 }
        val output = window.apply(input)

        // Endpoints of Hann window must be 0.0
        assertEquals(0.0, output.first(), 0.0001)
        assertEquals(0.0, output.last(), 0.0001)
        // Midpoint must be 1.0
        assertEquals(1.0, output[49], 0.05)
    }

    @Test
    fun `EMA smoother dampens sudden single outlier surge`() {
        val ema = ExponentialMovingAverage(alpha = 0.35)
        assertEquals(90, ema.update(90))
        val smoothed = ema.update(130)
        // 0.35 * 130 + 0.65 * 90 = 45.5 + 58.5 = 104
        assertEquals(104, smoothed)
    }

    @Test
    fun `detects low cadence 30 RPM from 0_5 Hz periodic acceleration`() {
        val pipeline = SignalProcessingPipeline()
        val freqHz = 0.50 // 0.50 Hz * 60 = 30 RPM
        val sampleIntervalNs = 20_000_000L // 50 Hz = 20 ms
        val totalSamples = 150 // 3.0 seconds

        for (i in 0 until totalSamples) {
            val tSec = (i * sampleIntervalNs) / 1_000_000_000.0
            val accel = (2.2 * sin(2.0 * PI * freqHz * tSec)).toFloat()
            val timestampNs = i * sampleIntervalNs
            pipeline.onSensorSample(timestampNs, 0f, accel, 0f)
        }

        val estimate = pipeline.processWindow()
        assertFalse("Should not be coasting with active pedaling at 30 RPM", estimate.isCoasting)
        assertEquals(30.0, estimate.rpm.toDouble(), 3.0)
    }

    @Test
    fun `detects 45 RPM cadence from 0_75 Hz periodic acceleration`() {
        val pipeline = SignalProcessingPipeline()
        val freqHz = 0.75 // 0.75 Hz * 60 = 45 RPM
        val sampleIntervalNs = 20_000_000L
        val totalSamples = 150

        for (i in 0 until totalSamples) {
            val tSec = (i * sampleIntervalNs) / 1_000_000_000.0
            val accel = (2.0 * sin(2.0 * PI * freqHz * tSec)).toFloat()
            val timestampNs = i * sampleIntervalNs
            pipeline.onSensorSample(timestampNs, accel, 0f, 0f)
        }

        val estimate = pipeline.processWindow()
        assertFalse("Should not be coasting", estimate.isCoasting)
        assertEquals(45.0, estimate.rpm.toDouble(), 3.0)
    }

    @Test
    fun `sensitivity adjustment scales noise threshold and detection`() {
        val pipeline = SignalProcessingPipeline()
        // 50% sensitivity default
        assertEquals(0.35, pipeline.extractor.noiseRmsThreshold, 0.001)

        // 100% sensitivity (highest)
        pipeline.setSensitivity(100)
        assertEquals(0.10, pipeline.extractor.noiseRmsThreshold, 0.001)
        assertEquals(0.10, pipeline.extractor.minPeakRatio, 0.001)

        // 0% sensitivity (lowest)
        pipeline.setSensitivity(0)
        assertEquals(0.60, pipeline.extractor.noiseRmsThreshold, 0.001)
        assertEquals(0.25, pipeline.extractor.minPeakRatio, 0.001)
    }
}
