package com.example.vitala

import com.example.vitala.data.TreatmentPlan
import com.example.vitala.data.Medication
import com.example.vitala.data.MedicationType
import com.example.vitala.data.PrescribedExercise
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class TreatmentPlanTest {

    @Test
    fun testTreatmentPlanMedicationFilters() {
        val medScheduled = Medication(
            name = "Aspirin",
            dose = "1 pill",
            type = MedicationType.SCHEDULED,
            times = listOf(LocalTime.of(8, 0))
        )
        val medAsNeeded = Medication(
            name = "Ibuprofen",
            dose = "1 pill",
            type = MedicationType.AS_NEEDED
        )

        val plan = TreatmentPlan(
            physicianName = "Dr. Smith",
            medications = listOf(medScheduled, medAsNeeded),
            exercises = listOf(PrescribedExercise(name = "Knee Extension", sets = 3, reps = 10))
        )

        assertEquals(1, plan.scheduledMedications().size)
        assertEquals(1, plan.asNeededMedications().size)
        assertEquals("Aspirin", plan.scheduledMedications().first().name)
        assertEquals("Ibuprofen", plan.asNeededMedications().first().name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyPlanThrowsException() {
        TreatmentPlan(
            physicianName = "Dr. Smith",
            exercises = emptyList(),
            medications = emptyList()
        )
    }
}
