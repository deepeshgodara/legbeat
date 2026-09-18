package com.legbeat.wear.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watchdog timer that triggers [onTimeout] if not fed within [timeoutMs] (default 5000 ms).
 * Used by Wear OS UI to transition to "--" when phone cadence stream stops or disconnects.
 */
class WatchdogTimer(
    val timeoutMs: Long = 5000L,
    val onTimeout: () -> Unit
) {
    private var job: Job? = null

    fun feed(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            delay(timeoutMs)
            onTimeout()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
