package com.legbeat.wear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.legbeat.core.model.CadenceZone
import com.legbeat.wear.data.CadencePacket
import com.legbeat.wear.data.WatchdogTimer
import com.legbeat.wear.data.WearCadenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearUiState(
    val rpm: Int? = null,
    val zone: CadenceZone = CadenceZone.IDLE,
    val isStale: Boolean = true,
    val isAmbient: Boolean = false,
    val burnInProtection: Boolean = false
)

@HiltViewModel
class WearCadenceViewModel @Inject constructor(
    application: Application,
    private val repository: WearCadenceRepository
) : AndroidViewModel(application), MessageClient.OnMessageReceivedListener {

    private val messageClient = Wearable.getMessageClient(application)

    private val _uiState = MutableStateFlow(WearUiState())
    val uiState: StateFlow<WearUiState> = _uiState.asStateFlow()

    private val watchdog = WatchdogTimer(timeoutMs = 120_000L) {
        // Timeout trigger: display "--"
        _uiState.value = _uiState.value.copy(
            rpm = null,
            isStale = true
        )
    }

    init {
        messageClient.addListener(this)

        // Observe repository flow
        viewModelScope.launch {
            repository.cadenceFlow.collect { packet ->
                handlePacket(packet)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        android.util.Log.d("WearCadenceVM", "onMessageReceived: ${messageEvent.path}")
        if (messageEvent.path == CadencePacket.CADENCE_PATH) {
            val packet = CadencePacket.parse(messageEvent.data)
            android.util.Log.d("WearCadenceVM", "parsed packet: $packet")
            if (packet != null) {
                handlePacket(packet)
            }
        }
    }

    private fun handlePacket(packet: CadencePacket) {
        android.util.Log.d("WearCadenceVM", "handlePacket: rpm=${packet.rpm}, zone=${packet.zone}")
        watchdog.feed(viewModelScope)
        _uiState.value = _uiState.value.copy(
            rpm = if (packet.rpm > 0) packet.rpm else null,
            zone = packet.zone,
            isStale = false
        )
    }

    fun simulateCadence(rpm: Int) {
        handlePacket(CadencePacket(rpm, CadenceZone.fromRpm(rpm), System.currentTimeMillis()))
    }

    fun onAmbientChanged(isAmbient: Boolean, burnInProtection: Boolean) {
        _uiState.value = _uiState.value.copy(
            isAmbient = isAmbient,
            burnInProtection = burnInProtection
        )
    }

    override fun onCleared() {
        super.onCleared()
        messageClient.removeListener(this)
        watchdog.stop()
    }
}
