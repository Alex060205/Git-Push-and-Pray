package com.example.vitala

import com.example.vitala.data.Patient
import org.junit.Assert.*
import org.junit.Test

class PatientTest {

    @Test
    fun testPatientCreationAndValidation() {
        val patient = Patient(
            name = "Jane Doe",
            heightCm = 165.0,
            weightKg = 60.0,
            affectedJoints = listOf("Wrist")
        )
        assertEquals("Jane Doe", patient.name)
        assertEquals(165.0, patient.bodyMetrics.heightCm, 0.01)
        assertEquals(60.0, patient.bodyMetrics.weightKg, 0.01)
        assertEquals(1, patient.bodyMetrics.affectedJoints.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlankNameThrowsException() {
        Patient(name = "", heightCm = 170.0, weightKg = 70.0, affectedJoints = listOf("Knee"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyJointsThrowsException() {
        Patient(name = "John", heightCm = 170.0, weightKg = 70.0, affectedJoints = emptyList())
    }
}
