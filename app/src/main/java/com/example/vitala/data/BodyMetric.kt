package com.example.vitala.data

import com.example.vitala.MetricsSource
import java.time.LocalDate

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