package com.legbeat.core.dsp

import kotlin.math.PI
import kotlin.math.cos

/**
 * Hann window implementation to mitigate spectral leakage across rolling window boundaries.
 * w[n] = 0.5 * (1 - cos(2 * PI * n / (N - 1)))
 */
class HannWindow {
    fun apply(input: DoubleArray): DoubleArray {
        val n = input.size
        if (n <= 1) return input.copyOf()

        val output = DoubleArray(n)
        val denom = (n - 1).toDouble()

        for (i in 0 until n) {
            val weight = 0.5 * (1.0 - cos(2.0 * PI * i / denom))
            output[i] = input[i] * weight
        }
        return output
    }
}
