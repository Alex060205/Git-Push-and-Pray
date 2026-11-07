package com.example.vitala.data


/**
 * An exercise adjusted for one day (e.g. knee bends cut from 2 sets to 1).
 */
data class ExerciseItem(
    val exerciseId: String,              // PrescribedExercise.id
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val durationMinutes: Int? = null
)