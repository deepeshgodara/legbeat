package com.legbeat.core.dsp

/**
 * Single instantaneous 3D accelerometer sample.
 */
data class AccelSample(
    val timestampNs: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Thread-safe sliding ring buffer maintaining a rolling time window of accelerometer samples.
 * Default duration: 3.0 seconds (3,000,000,000 ns).
 */
class RollingWindowBuffer(
    val windowDurationNs: Long = 3_000_000_000L
) {
    private val buffer = ArrayDeque<AccelSample>(300)

    @Synchronized
    fun addSample(timestampNs: Long, x: Float, y: Float, z: Float) {
        val sample = AccelSample(timestampNs, x, y, z)
        buffer.addLast(sample)

        val cutoff = timestampNs - windowDurationNs
        while (buffer.isNotEmpty() && buffer.first().timestampNs < cutoff) {
            buffer.removeFirst()
        }
    }

    @Synchronized
    fun getSnapshot(): List<AccelSample> = buffer.toList()

    @Synchronized
    fun size(): Int = buffer.size

    @Synchronized
    fun clear() {
        buffer.clear()
    }
}
