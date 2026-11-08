package com.example.vitala

import com.example.vitala.data.Calendar
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.AvailabilityPreferences
import com.example.vitala.data.TimeBlock
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalTime

class CalendarTest {

    @Test
    fun testDaysInMonth() {
        val calendar = Calendar(yearMonth = YearMonth.of(2024, 2)) // Leap year
        val days = calendar.daysInMonth
        assertEquals(29, days.size)
        assertEquals(LocalDate.of(2024, 2, 1), days.first())
        assertEquals(LocalDate.of(2024, 2, 29), days.last())
    }

    @Test
    fun testMonthNavigation() {
        val calendar = Calendar(yearMonth = YearMonth.of(2024, 5))
        val next = calendar.nextMonth()
        assertEquals(YearMonth.of(2024, 6), next.yearMonth)

        val prev = calendar.previousMonth()
        assertEquals(YearMonth.of(2024, 4), prev.yearMonth)
    }

    @Test
    fun testDaySessionFiltering() {
        val date = LocalDate.of(2024, 5, 10)
        val session = ExerciseSession(date = date.toString(), items = emptyList())
        val availability = AvailabilityPreferences(
            blocksToAvoid = listOf(
                TimeBlock(start = LocalTime.of(12, 0), end = LocalTime.of(13, 0), days = setOf(date.dayOfWeek))
            )
        )

        val calendar = Calendar(
            selectedDate = date,
            allExerciseSessions = listOf(session),
            availabilityPreferences = availability
        )

        val daySession = calendar.currentDaySession
        assertEquals(1, daySession.exerciseSessions.size)
        assertEquals(1, daySession.notAvailableBlocks.size)
        assertTrue(daySession.isNotAvailable(LocalTime.of(12, 30)))
        assertFalse(daySession.isNotAvailable(LocalTime.of(10, 0)))
    }
}
