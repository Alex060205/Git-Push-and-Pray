package com.example.vitala

import java.time.LocalDate
import java.time.LocalTime

/** An exercise adjusted for one day (e.g. knee bends cut from 2 sets to 1). */
data class ExerciseItem(
    val exerciseId: String,              // PrescribedExercise.id
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val durationMinutes: Int? = null
)

/** One choice the patient can pick: Gentle, Full plan, or Rest. */
data class ExerciseOption(
    val intensity: Intensity,
    val title: String,
    val items: List<ExerciseItem>,
    val durationMinutes: Int,
    val isSuggested: Boolean = false,
    val reason: String = ""
)

data class PainForecast(
    val date: LocalDate,
    val minLevel: Int,
    val maxLevel: Int,
    val confidence: Confidence,
    val factors: List<String> = emptyList(),
    val actionPlan: String
) {
    init {
        require(minLevel in 0..10 && maxLevel in 0..10) { "Levels must be 0-10" }
        require(minLevel <= maxLevel) { "minLevel must be <= maxLevel" }
    }
}

/** Everything the Today screen shows. */
data class DailyPlan(
    val date: LocalDate,
    val weather: WeatherSnapshot?,
    val message: String,                 // e.g. "Dress warm, take your time..."
    val options: List<ExerciseOption>,
    val chosenOption: ExerciseOption? = null,
    val exerciseTime: LocalTime? = null,
    val medicationsDue: List<MedicationLog> = emptyList(),
    val forecast: PainForecast? = null,
    val checkInDone: Boolean = false
) {
    val suggestedOption: ExerciseOption?
        get() = options.firstOrNull { it.isSuggested }
}

/** Overall pain trend for the History screen ("Getting better"). */
data class PainTrend(
    val from: LocalDate,
    val to: LocalDate,
    val averageFirstHalf: Double,
    val averageSecondHalf: Double,
    val direction: TrendDirection
)

/** One finding, e.g. "You felt better after nights with 7 or more hours of sleep." */
data class Insight(
    val factor: InsightFactor,
    val painDifference: Double,          // avg pain without factor - avg pain with factor
    val text: String
)