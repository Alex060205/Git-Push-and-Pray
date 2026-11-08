package com.example.vitala.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A Calendar utility and model for managing month views, day sessions,
 * exercise sessions, and not-available time blocks.
 */
class Calendar(
    val yearMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    private val allExerciseSessions: List<ExerciseSession> = emptyList(),
    private val availabilityPreferences: AvailabilityPreferences = AvailabilityPreferences()
) {
    /**
     * Returns a list of all dates in the current month.
     */
    val daysInMonth: List<LocalDate>
        get() {
            val firstDay = yearMonth.atDay(1)
            val lastDay = yearMonth.atEndOfMonth()
            val days = mutableListOf<LocalDate>()
            var current = firstDay
            while (!current.isAfter(lastDay)) {
                days.add(current)
                current = current.plusDays(1)
            }
            return days
        }

    /**
     * Returns a calendar grid (weeks as lists of LocalDate) for the month,
     * padding with days from adjacent months to complete full weeks.
     */
    fun getCalendarGrid(startOfWeek: DayOfWeek = DayOfWeek.SUNDAY): List<List<LocalDate>> {
        val firstDayOfMonth = yearMonth.atDay(1)
        val lastDayOfMonth = yearMonth.atEndOfMonth()

        val weeks = mutableListOf<MutableList<LocalDate>>()
        var currentWeek = mutableListOf<LocalDate>()

        val dayValue = firstDayOfMonth.dayOfWeek.value
        val targetStartValue = startOfWeek.value
        val paddingDays = (dayValue - targetStartValue + 7) % 7

        var currentDate = firstDayOfMonth.minusDays(paddingDays.toLong())

        while (!currentDate.isAfter(lastDayOfMonth) || currentWeek.isNotEmpty()) {
            currentWeek.add(currentDate)
            if (currentWeek.size == 7) {
                weeks.add(currentWeek)
                currentWeek = mutableListOf()
            }
            currentDate = currentDate.plusDays(1)
            if (currentDate.isAfter(lastDayOfMonth) && currentWeek.isEmpty()) {
                break
            }
        }
        return weeks
    }

    /**
     * Returns the DaySession for a specific date, including its exercise sessions
     * and not-available time blocks.
     */
    fun getDaySession(date: LocalDate): DaySession {
        val sessionsForDate = allExerciseSessions.filter { session ->
            session.date == date.toString()
        }

        val dayOfWeek = date.dayOfWeek
        val notAvailable = availabilityPreferences.blocksToAvoid.filter { block ->
            dayOfWeek in block.days
        }

        return DaySession(
            date = date,
            exerciseSessions = sessionsForDate,
            notAvailableBlocks = notAvailable
        )
    }

    /**
     * Returns the DaySession for the currently selected date.
     */
    val currentDaySession: DaySession
        get() = getDaySession(selectedDate)

    /**
     * Returns a new Calendar instance with a different selected date.
     */
    fun selectDate(date: LocalDate): Calendar = Calendar(
        yearMonth = YearMonth.from(date),
        selectedDate = date,
        allExerciseSessions = allExerciseSessions,
        availabilityPreferences = availabilityPreferences
    )

    /**
     * Formats the month and year for display (e.g. "November 2024").
     */
    fun formatMonthTitle(): String {
        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$monthName ${yearMonth.year}"
    }

    /**
     * Moves to the next month.
     */
    fun nextMonth(): Calendar = Calendar(
        yearMonth = yearMonth.plusMonths(1),
        selectedDate = selectedDate.plusMonths(1),
        allExerciseSessions = allExerciseSessions,
        availabilityPreferences = availabilityPreferences
    )

    /**
     * Moves to the previous month.
     */
    fun previousMonth(): Calendar = Calendar(
        yearMonth = yearMonth.minusMonths(1),
        selectedDate = selectedDate.minusMonths(1),
        allExerciseSessions = allExerciseSessions,
        availabilityPreferences = availabilityPreferences
    )
}

/**
 * Represents the sessions and unavailable time blocks for a specific day.
 */
data class DaySession(
    val date: LocalDate,
    val exerciseSessions: List<ExerciseSession> = emptyList(),
    val notAvailableBlocks: List<TimeBlock> = emptyList()
) {
    /**
     * Checks if a given time falls within any not-available time block on this day.
     */
    fun isNotAvailable(time: LocalTime): Boolean {
        val dayOfWeek = date.dayOfWeek
        return notAvailableBlocks.any { it.contains(dayOfWeek, time) }
    }
}
