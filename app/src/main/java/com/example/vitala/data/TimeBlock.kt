package com.example.vitala.data

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID


/**
 * Examples:
 *  Before 9:00 am every day -> TimeBlock(LocalTime.MIN, LocalTime.of(9, 0), EVERY_DAY)
 *  All day Sundays          -> TimeBlock(LocalTime.MIN, LocalTime.MAX, setOf(DayOfWeek.SUNDAY))
 *  @param id Id of the time block
 *  @param start Start time of the time block
 *  @param end End time of the time block
 *  @param days Days of the week the time block applies to
 *  @constructor Creates a new time block object with the given parameters
 *  @throws IllegalArgumentException If the start time is after the end time
 */
data class TimeBlock(
    val id: String = UUID.randomUUID().toString(),
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<DayOfWeek> = EVERY_DAY
) {
    init {
        require(start < end) { "Start must be before end" }
    }

    /**
     * Checks if a given day and time is within the time block.
     * @param day Day of the week to check
     * @param time Time to check
     * @return True if the time is within the time block, false otherwise
     */
    fun contains(day: DayOfWeek, time: LocalTime): Boolean =
        day in days && time >= start && time < end

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}