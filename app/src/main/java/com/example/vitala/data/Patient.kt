package com.example.vitala.data

import java.time.LocalDate
import java.util.UUID

/**
 * A patient's profile.
 */
data class Patient(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val birthDate: LocalDate,
    val bodyMetrics: BodyMetrics,
    val activePlanId: String? = null,
    val availability: AvailabilityPreferences = AvailabilityPreferences()
)
