package com.example.vitala

import com.example.vitala.data.Medication
import com.example.vitala.data.MedicationType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class MedicationTest {

    @Test
    fun testScheduledMedicationValid() {
        val med = Medication(
            name = "Naproxen",
            dose = "500mg",
            type = MedicationType.SCHEDULED,
            times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))
        )
        assertEquals("Naproxen", med.name)
        assertEquals(2, med.times.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testScheduledMedicationNeedsTimes() {
        Medication(
            name = "Naproxen",
            dose = "500mg",
            type = MedicationType.SCHEDULED,
            times = emptyList()
        )
    }

    @Test
    fun testAsNeededMedicationNoTimesNeeded() {
        val med = Medication(
            name = "Tylenol",
            dose = "325mg",
            type = MedicationType.AS_NEEDED,
            times = emptyList()
        )
        assertEquals(MedicationType.AS_NEEDED, med.type)
    }
}
