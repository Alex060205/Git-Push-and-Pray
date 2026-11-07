package com.example.vitala.data

import java.time.DayOfWeek
import java.util.UUID


/**
 * Rep-based (knee bends: sets = 2, reps = 10) or time-based (walk: durationMinutes = 15).
 * @param id Unique identifier
 * @param name Name of the exercise
 * @param sets Number of sets
 * @param reps Number of reps
 * @param durationMinutes Duration in minutes
 * @param days Days of the week
 * @param instructions Instructions for the exercise
 * @constructor Creates a new prescribed exercise object with the given parameters
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