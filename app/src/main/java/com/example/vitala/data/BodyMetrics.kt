package com.example.vitala.data

import java.time.LocalDate

/**
 * The bodymetric of the patience
 * @param heightCm Height in cm
 * @param weightKg Weight in kg
 * @param affectedJoints List of affected joints
 * @param updatedOn Date the metrics were updated
 * @param source Source of the metrics
 * @constructor Creates a new bodymetric object with the given parameters
 */
data class BodyMetrics(
    val heightCm: Double,
    val weightKg: Double,
    val affectedJoints: List<String> = emptyList(),   // e.g. "Left knee"
    val updatedOn: LocalDate = LocalDate.now(),
) {
    init {
        require(heightCm > 0) { "Height must be positive" }
        require(weightKg > 0) { "Weight must be positive" }
    }
}