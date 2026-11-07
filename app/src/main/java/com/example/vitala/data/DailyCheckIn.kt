package com.example.vitala.data

import java.time.LocalDate
import java.util.UUID

/**
 * One per day. Weather is attached automatically, not asked.
 * @param id Unique identifier
 * @param date Date of the check-in
 * @param painLevel Pain level
 * @param painDuration Duration of pain
 * @param sleepHours Hours of sleep
 * @param mood Mood
 * @param nutrition Nutrition
 * @param tookAllMedications Whether the patient took all their medications
 * @param weather Weather at the time of the check-in
 * @param exerciseIntensity The intensity of the exercise done the day before
 * @constructor Creates a new daily check-in object with the given parameters
 */
data class DailyCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val painLevel: Int,                  // 0..10
    val painDuration: PainDuration,
    val sleepHours: Double,
    val mood: Mood,
    val nutrition: Nutrition,
    val tookAllMedications: Boolean,
    val weather: WeatherSnapshot? = null,
    val exerciseIntensity: Intensity
) {
    init {
        require(painLevel in 0..10) { "Pain level must be 0-10" }
        require(sleepHours in 0.0..24.0) { "Sleep hours must be 0-24" }
    }
}