package com.legbeat.core.ble

import kotlinx.coroutines.flow.Flow

/**
 * Common abstraction for Bluetooth Low Energy cycling peripherals.
 * Designed to scale LegBeat for external sensors (Heart Rate Monitors, Power Meters).
 */
sealed interface BlePeripheral {
    val deviceAddress: String
    val deviceName: String
}

data class BleHeartRateSample(
    val timestampMs: Long,
    val heartRateBpm: Int,
    val rrIntervalsMs: List<Int> = emptyList()
)

data class BlePowerSample(
    val timestampMs: Long,
    val instantaneousPowerWatts: Int,
    val pedalCadenceRpm: Int? = null,
    val leftRightBalancePercent: Float? = null
)

/**
 * Service contract for GATT 0x180D Heart Rate Service.
 */
interface BleHeartRateService {
    fun connect(deviceAddress: String): Flow<BleConnectionState>
    fun observeHeartRate(): Flow<BleHeartRateSample>
    fun disconnect()
}

/**
 * Service contract for GATT 0x1818 Cycling Power Service.
 */
interface BlePowerMeterService {
    fun connect(deviceAddress: String): Flow<BleConnectionState>
    fun observePower(): Flow<BlePowerSample>
    fun disconnect()
}

/**
 * Generic scanner for discovering cycling peripherals.
 */
interface BleSensorScanner {
    fun scanPeripherals(filterServices: List<String>): Flow<BlePeripheral>
    fun stopScan()
}

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}
