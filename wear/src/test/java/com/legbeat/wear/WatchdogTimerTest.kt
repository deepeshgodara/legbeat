package com.legbeat.wear

import com.legbeat.core.model.CadenceZone
import com.legbeat.wear.data.CadencePacket
import com.legbeat.wear.data.WatchdogTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
class WatchdogTimerTest {

    @Test
    fun `parses valid binary cadence packet`() {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(88) // 88 RPM
        buffer.putInt(CadenceZone.ENDURANCE.code) // Endurance zone
        buffer.putLong(1700000000000L) // Timestamp

        val packet = CadencePacket.parse(buffer.array())
        assertNotNull(packet)
        assertEquals(88, packet!!.rpm)
        assertEquals(CadenceZone.ENDURANCE, packet.zone)
        assertEquals(1700000000000L, packet.timestampMs)
    }

    @Test
    fun `watchdog triggers timeout after 5000ms of inactivity`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var timeoutTriggered = false
        val watchdog = WatchdogTimer(timeoutMs = 5000L) {
            timeoutTriggered = true
        }

        watchdog.feed(testScope)

        // Advance 4900ms (should not trigger yet)
        testScope.advanceTimeBy(4900L)
        assertFalse("Should not trigger before 5000ms", timeoutTriggered)

        // Advance past 5000ms
        testScope.advanceTimeBy(200L)
        assertTrue("Must trigger timeout after 5000ms", timeoutTriggered)
    }

    @Test
    fun `watchdog feeding resets timeout window`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var timeoutTriggered = false
        val watchdog = WatchdogTimer(timeoutMs = 5000L) {
            timeoutTriggered = true
        }

        watchdog.feed(testScope)
        testScope.advanceTimeBy(3000L) // 3 seconds passed

        // Feed watchdog at 3s
        watchdog.feed(testScope)

        // Advance another 3 seconds (total 6s from start, but 3s from feed)
        testScope.advanceTimeBy(3000L)
        assertFalse("Should not trigger because watchdog was fed", timeoutTriggered)

        // Advance another 2.1 seconds (5.1s since last feed)
        testScope.advanceTimeBy(2100L)
        assertTrue("Must trigger after 5000ms from last feed", timeoutTriggered)
    }
}
