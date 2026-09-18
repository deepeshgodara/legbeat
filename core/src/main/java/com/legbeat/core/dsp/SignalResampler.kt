package com.legbeat.core.dsp

import kotlin.math.sqrt

/**
 * Resamples non-uniformly spaced accelerometer 3D samples into uniformly
 * spaced samples at a target sampling frequency (default Fs = 50 Hz, Ts = 20 ms),
 * and projects 3D acceleration onto the principal motion axis (PCA).
 *
 * Projecting onto the primary component preserves the true pedal harmonic frequency
 * without full-wave rectification artifacts (which double the frequency when using ||a||).
 */
class SignalResampler(
    val targetSampleRateHz: Double = 50.0
) {
    fun resample(samples: List<AccelSample>, targetCount: Int = 150): DoubleArray {
        if (samples.size < 2) {
            return DoubleArray(targetCount)
        }

        val xs = DoubleArray(targetCount)
        val ys = DoubleArray(targetCount)
        val zs = DoubleArray(targetCount)

        val startTimeNs = samples.first().timestampNs
        val endTimeNs = samples.last().timestampNs
        val totalDurationNs = endTimeNs - startTimeNs

        if (totalDurationNs <= 0) {
            return DoubleArray(targetCount)
        }

        val stepNs = totalDurationNs.toDouble() / (targetCount - 1)
        var sampleIndex = 0

        for (i in 0 until targetCount) {
            val queryTimeNs = startTimeNs + i * stepNs

            while (sampleIndex < samples.size - 2 && samples[sampleIndex + 1].timestampNs < queryTimeNs) {
                sampleIndex++
            }

            val p0 = samples[sampleIndex]
            val p1 = samples[sampleIndex + 1]

            val dt = (p1.timestampNs - p0.timestampNs).toDouble()
            val fraction = if (dt > 0) ((queryTimeNs - p0.timestampNs) / dt).coerceIn(0.0, 1.0) else 0.0

            xs[i] = p0.x + fraction * (p1.x - p0.x)
            ys[i] = p0.y + fraction * (p1.y - p0.y)
            zs[i] = p0.z + fraction * (p1.z - p0.z)
        }

        return projectOntoPrincipalAxis(xs, ys, zs, targetCount)
    }

    private fun projectOntoPrincipalAxis(
        xs: DoubleArray,
        ys: DoubleArray,
        zs: DoubleArray,
        n: Int
    ): DoubleArray {
        var mx = 0.0
        var my = 0.0
        var mz = 0.0
        for (i in 0 until n) {
            mx += xs[i]
            my += ys[i]
            mz += zs[i]
        }
        mx /= n
        my /= n
        mz /= n

        var cxx = 0.0
        var cxy = 0.0
        var cxz = 0.0
        var cyy = 0.0
        var cyz = 0.0
        var czz = 0.0

        for (i in 0 until n) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            val dz = zs[i] - mz
            cxx += dx * dx
            cxy += dx * dy
            cxz += dx * dz
            cyy += dy * dy
            cyz += dy * dz
            czz += dz * dz
        }

        var vx = 1.0
        var vy = 1.0
        var vz = 1.0

        for (iter in 0 until 5) {
            val nx = cxx * vx + cxy * vy + cxz * vz
            val ny = cxy * vx + cyy * vy + cyz * vz
            val nz = cxz * vx + cyz * vy + czz * vz
            val norm = sqrt(nx * nx + ny * ny + nz * nz)
            if (norm > 1e-9) {
                vx = nx / norm
                vy = ny / norm
                vz = nz / norm
            }
        }

        val projected = DoubleArray(n)
        for (i in 0 until n) {
            projected[i] = (xs[i] - mx) * vx + (ys[i] - my) * vy + (zs[i] - mz) * vz
        }

        return projected
    }
}
