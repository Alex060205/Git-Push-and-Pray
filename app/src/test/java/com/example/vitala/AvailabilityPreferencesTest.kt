package com.example.vitala

import com.example.vitala.data.AvailabilityPreferences
import com.example.vitala.data.TimeBlock
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class AvailabilityPreferencesTest {

    @Test
    fun testAvailabilityPreferencesAddAndAvoid() {
        val prefs = AvailabilityPreferences()
        val block = TimeBlock(
            start = LocalTime.of(13, 0),
            end = LocalTime.of(14, 0),
            days = setOf(DayOfWeek.WEDNESDAY)
        )

        val updated = prefs.add(block)
        assertEquals(1, updated.blocksToAvoid.size)
        assertTrue(updated.isAvoided(DayOfWeek.WEDNESDAY, LocalTime.of(13, 30)))
        assertFalse(updated.isAvoided(DayOfWeek.WEDNESDAY, LocalTime.of(15, 0)))
        assertFalse(updated.isAvoided(DayOfWeek.THURSDAY, LocalTime.of(13, 30)))

        val removed = updated.remove(block.id)
        assertEquals(0, removed.blocksToAvoid.size)
    }
}
