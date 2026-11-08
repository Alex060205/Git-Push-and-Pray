package com.example.vitala

import com.example.vitala.data.TimeBlock
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class TimeBlockTest {

    @Test
    fun testTimeBlockContains() {
        val block = TimeBlock(
            start = LocalTime.of(9, 0),
            end = LocalTime.of(11, 0),
            days = setOf(DayOfWeek.MONDAY)
        )

        assertTrue(block.contains(DayOfWeek.MONDAY, LocalTime.of(10, 0)))
        assertTrue(block.contains(DayOfWeek.MONDAY, LocalTime.of(9, 0)))
        assertFalse(block.contains(DayOfWeek.MONDAY, LocalTime.of(11, 0)))
        assertFalse(block.contains(DayOfWeek.TUESDAY, LocalTime.of(10, 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidTimeBlockThrowsException() {
        TimeBlock(start = LocalTime.of(12, 0), end = LocalTime.of(10, 0))
    }
}
