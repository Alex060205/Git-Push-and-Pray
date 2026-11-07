package com.example.vitala

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** The physician's prescription. Acts as the upper limit for any exercise adjustment.
 *
 */
data class TreatmentPlan(
    val id: String = UUID.randomUUID().toString(),
    val physicianName: String,
    val createdOn: LocalDate,
    val version: Int = 1,
    val exercises: List<PrescribedExercise>,
    val medications: List<Medication>,
    val isActive: Boolean = true
) {
    /**
     *
     */
    fun exercisesFor(date: LocalDate): List<PrescribedExercise> =
        exercises.filter { date.dayOfWeek in it.days }

    fun scheduledMedications(): List<Medication> =
        medications.filter { it.type == MedicationType.SCHEDULED }

    fun asNeededMedications(): List<Medication> =
        medications.filter { it.type == MedicationType.AS_NEEDED }
}

/**
 * Rep-based (knee bends: sets = 2, reps = 10) or time-based (walk: durationMinutes = 15).
 */
data class PrescribedExercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val durationMinutes: Int? = null,
    val days: Set<DayOfWeek> = TimeBlock.EVERY_DAY,
    val instructions: String = ""
) {
    init {
        require((sets != null && reps != null) || durationMinutes != null) {
            "Exercise needs sets + reps or a duration"
        }
    }
}

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dose: String,                         // e.g. "1 tablet"
    val type: MedicationType,
    val times: List<LocalTime> = emptyList(), // only for SCHEDULED
    val notes: String = ""                    // e.g. "with food"
) {
    init {
        require(type == MedicationType.AS_NEEDED || times.isNotEmpty()) {
            "Scheduled medication needs at least one time"
        }
    }
}