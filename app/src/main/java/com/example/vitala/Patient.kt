package com.example.vitala

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class Patient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val birthDate: LocalDate,
    val bodyMetrics: BodyMetrics,
    val activePlanId: String? = null,
    val availability: AvailabilityPreferences = AvailabilityPreferences()
)

data class BodyMetrics(
    val heightCm: Double,
    val weightKg: Double,
    val affectedJoints: List<String> = emptyList(),   // e.g. "Left knee"
    val updatedOn: LocalDate,
    val source: MetricsSource
) {
    init {
        require(heightCm > 0) { "Height must be positive" }
        require(weightKg > 0) { "Weight must be positive" }
    }
}

/** Times the patient does not want exercises suggested ("Times to avoid" screen). */
data class AvailabilityPreferences(
    val blocksToAvoid: List<TimeBlock> = emptyList()
) {
    fun isAvoided(day: DayOfWeek, time: LocalTime): Boolean =
        blocksToAvoid.any { it.contains(day, time) }

    fun add(block: TimeBlock) = copy(blocksToAvoid = blocksToAvoid + block)

    fun remove(blockId: String) = copy(blocksToAvoid = blocksToAvoid.filterNot { it.id == blockId })
}

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