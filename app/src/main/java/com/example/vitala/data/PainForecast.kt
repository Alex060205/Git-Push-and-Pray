package com.example.vitala.data

import java.time.LocalDate

/**
 * A forecast of pain for a given day.
 * @param date Date of the forecast
 * @param minLevel Minimum level of pain expected
 * @param maxLevel Maximum level of pain expected
 * @param confidence Confidence of the forecast
 * @param factors List of factors that contribute to the forecast
 * @param actionPlan Action plan for the patient based on the forecast
 * @constructor Creates a new pain forecast object with the given parameters
 */
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