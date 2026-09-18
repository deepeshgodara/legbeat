package com.legbeat.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure Kotlin in-place Radix-2 Cooley-Tukey Fast Fourier Transform (FFT).
 * Operates on arrays whose length is a power of 2 (e.g. 256, 512, 1024).
 */
class CooleyTukeyFft {

    /**
     * In-place Radix-2 FFT computation.
     * @param real Real part array (length must be power of 2)
     * @param imag Imaginary part array (length must match real.size)
     */
    fun transform(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2, was $n" }
        require(imag.size == n) { "Imaginary array size must match real array size" }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey decimation-in-time
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len
            val wStepR = cos(angle)
            val wStepI = sin(angle)

            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0

                for (k in 0 until halfLen) {
                    val posEven = i + k
                    val posOdd = posEven + halfLen

                    val uR = real[posEven]
                    val uI = imag[posEven]

                    val vR = real[posOdd] * wR - imag[posOdd] * wI
                    val vI = real[posOdd] * wI + imag[posOdd] * wR

                    real[posEven] = uR + vR
                    imag[posEven] = uI + vI

                    real[posOdd] = uR - vR
                    imag[posOdd] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Computes the one-sided power spectral density: P[k] = (Re[k]^2 + Im[k]^2) / N.
     * Returns an array of size N/2.
     */
    fun computePowerSpectrum(real: DoubleArray, imag: DoubleArray): DoubleArray {
        val n = real.size
        val halfN = n / 2
        val power = DoubleArray(halfN)
        for (k in 0 until halfN) {
            power[k] = (real[k] * real[k] + imag[k] * imag[k]) / n
        }
        return power
    }
}
