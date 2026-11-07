package com.example.vitala.data

import java.time.LocalDate

/**
 * The bodymetric of the patience
 */
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