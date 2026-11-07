<<<<<<< HEAD
package com.example.vitala.data

import java.time.DayOfWeek
import java.time.LocalTime


/**
 * The patient's availability preferences.
 * Examples:
 *  Before 9:00 am every day -> TimeBlock(LocalTime.MIN, LocalTime.of(9, 0), EVERY_DAY)
 *  All day Sundays          -> TimeBlock(LocalTime.MIN, LocalTime.MAX, setOf(DayOfWeek.SUNDAY))
 *  @param blocksToAvoid List<TimeBlock> - List of time blocks to avoid
 *  @constructor Creates a new availability preferences object with the given time blocks to avoid
 */
=======
import com.example.vitala.TimeBlock
import java.time.DayOfWeek
import java.time.LocalTime

/** Times the patient does not want exercises suggested ("Times to avoid" screen).*/
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
data class AvailabilityPreferences(
    val blocksToAvoid: List<TimeBlock> = emptyList()
) {
    /**
<<<<<<< HEAD
     * Checks if a time block is avoided for a given day and time.
     * @param day Day of the week to check
     * @param time Time to check
     * @return True if the time block is avoided, false otherwise
=======
     * determines whether or  not the give time block is available
     * @param day, in the type DayofWeek, e.g. DayOfWeek.MONDAY
     * @param time,
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
     */
    fun isAvoided(day: DayOfWeek, time: LocalTime): Boolean =
        blocksToAvoid.any { it.contains(day, time) }

<<<<<<< HEAD
    /**
     * Adds a time block to the list of time blocks to avoid.
     * @param block TimeBlock to add
     * @return New AvailabilityPreferences object with the added time block
     */
    fun add(block: TimeBlock) = copy(blocksToAvoid = blocksToAvoid + block)

    /**
     * Removes a time block from the list of time blocks to avoid.
     * @param blockId Id of the time block to remove
     * @return New AvailabilityPreferences object with the removed time block
     */
=======
    fun add(block: TimeBlock) = copy(blocksToAvoid = blocksToAvoid + block)

>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
    fun remove(blockId: String) = copy(blocksToAvoid = blocksToAvoid.filterNot { it.id == blockId })
}