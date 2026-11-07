<<<<<<< HEAD
package com.example.vitala.data

=======
package com.example.vitala

import AvailabilityPreferences
import com.example.vitala.data.BodyMetrics
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
<<<<<<< HEAD
 * A patient's profile.
=======
 *
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
 */
data class Patient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val birthDate: LocalDate,
    val bodyMetrics: BodyMetrics,
    val activePlanId: String? = null,
    val availability: AvailabilityPreferences = AvailabilityPreferences()
<<<<<<< HEAD
)
=======
)

/**
 * Examples:
 *  Before 9:00 am every day -> TimeBlock(LocalTime.MIN, LocalTime.of(9, 0), EVERY_DAY)
 *  All day Sundays          -> TimeBlock(LocalTime.MIN, LocalTime.MAX, setOf(DayOfWeek.SUNDAY))
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

    fun contains(day: DayOfWeek, time: LocalTime): Boolean =
        day in days && time >= start && time < end

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
