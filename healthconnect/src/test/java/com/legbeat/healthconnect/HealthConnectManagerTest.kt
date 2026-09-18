package com.legbeat.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectManagerTest {

    @Test
    fun `required permissions cover exercise session and cadence write`() {
        val exercisePerm = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        val cadencePerm = HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class)

        val permissions = setOf(exercisePerm, cadencePerm)

        assertEquals(2, permissions.size)
        assertTrue(permissions.any { it.contains("exercise", ignoreCase = true) })
        assertTrue(permissions.any { it.contains("cadence", ignoreCase = true) })
    }
}
