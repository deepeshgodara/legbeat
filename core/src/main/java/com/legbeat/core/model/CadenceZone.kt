package com.legbeat.core.model

/**
 * Standard cycling cadence training zones based on pedaling biomechanics.
 * - Recovery: < 70 RPM (low neuromuscular load, warm-up or active recovery)
 * - Endurance: 70 - 89 RPM (aerobic efficiency, cruising)
 * - Tempo: 90 - 100 RPM (optimal crank power, racing cadence)
 * - High-Spin: > 100 RPM (neuromuscular sprint, steep gradient spin)
 */
enum class CadenceZone(
    val code: Int,
    val label: String,
    val minRpm: Int,
    val maxRpm: Int,
    val colorHex: Long
) {
    IDLE(0, "Idle", 0, 0, 0xFF9E9E9E),
    RECOVERY(1, "Recovery", 1, 69, 0xFF4FC3F7),
    ENDURANCE(2, "Endurance", 70, 89, 0xFF81C784),
    TEMPO(3, "Tempo", 90, 100, 0xFFFFD54F),
    HIGH_SPIN(4, "High Spin", 101, 220, 0xFFFF7043);

    companion object {
        fun fromRpm(rpm: Int): CadenceZone = when {
            rpm <= 0 -> IDLE
            rpm < 70 -> RECOVERY
            rpm <= 89 -> ENDURANCE
            rpm <= 100 -> TEMPO
            else -> HIGH_SPIN
        }

        fun fromCode(code: Int): CadenceZone = entries.firstOrNull { it.code == code } ?: IDLE
    }
}
