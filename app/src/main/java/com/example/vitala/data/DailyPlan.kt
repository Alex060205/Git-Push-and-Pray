package com.example.vitala.data

import java.time.LocalDate
import java.time.LocalTime


/**
 * A plan for one day.
 * @param date Date of the plan
 * @param weather Weather at the time of the plan
 * @param message Message to display to the patient
 * @param options List of exercise options
 * @param chosenOption Chosen exercise option
 * @param exerciseTime Time to start exercise
 * @param medicationsDue List of medications due
 * @param forecast Pain forecast
 * @param checkInDone Whether the check-in has been done
 * @constructor Creates a new daily plan object with the given parameters
 * @property suggestedOption Suggested exercise option
 */
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