package com.example.vitala.data


/**
 * One choice the patient can pick: Gentle, Full plan, or Rest.
 */
data class ExerciseOption(
    val intensity: Intensity,
    val title: String,
    val items: List<ExerciseItem>,
    val durationMinutes: Int,
    val isSuggested: Boolean = false,
    val reason: String = ""
)