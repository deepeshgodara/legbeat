package com.legbeat.wear.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearCadenceRepository @Inject constructor() {

    private val _cadenceFlow = MutableSharedFlow<CadencePacket>(extraBufferCapacity = 16)
    val cadenceFlow: SharedFlow<CadencePacket> = _cadenceFlow.asSharedFlow()

    fun onPacketReceived(packet: CadencePacket) {
        _cadenceFlow.tryEmit(packet)
    }
}
