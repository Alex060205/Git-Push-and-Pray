package com.example.vitala.data

import com.google.firebase.firestore.DocumentId
import java.time.LocalDate
import java.util.UUID

/**
 * A patient's profile.
 * @param id Unique identifier
 * @param name Name
 * @param birthDate Date of birth
 * @param gender Gender
 * @param heightCm Height in cm
 * @param weightKg Weight in kg
 * @param affectedJoints List of affected joints
 * @param availability Availability preferences
 * @constructor Creates a new patient object with the given parameters
 * @property bodyMetrics Body metrics
 * @property treatmentPlan Treatment plan
 */
data class Patient(
    @DocumentId val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val birthDate: LocalDate = LocalDate.now(),
    val gender: String = "",
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val affectedJoints: List<String> = emptyList(),

    val activePlanId: String = UUID.randomUUID().toString(),
    val exercise: List<PrescribedExercise> = emptyList(),
    val medications: List<Medication> = emptyList(),

    val availability: AvailabilityPreferences = AvailabilityPreferences(),
) {
    val bodyMetrics: BodyMetrics = BodyMetrics(heightCm, weightKg, affectedJoints)
    val treatmentPlan: TreatmentPlan = TreatmentPlan(
        id = activePlanId, physicianName = "",
        createdOn = LocalDate.now(),
        exercises = exercise,
        medications = medications
    )

    init {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(bodyMetrics.heightCm > 0) { "Height must be positive" }
        require(bodyMetrics.weightKg > 0) { "Weight must be positive" }
        require(bodyMetrics.affectedJoints.isNotEmpty()) { "At least one joint must be affected" }
    }

    /**
     * Returns the exercises that apply to the given date.
     * @param date Date to check
     * @return List of exercises that apply to the given date
     *          (empty if no exercises apply)
     */
    fun exercisesFor(date: LocalDate): List<PrescribedExercise> =
        treatmentPlan.exercises.filter { date.dayOfWeek in it.days }

    /**
     * Returns the scheduled medications.
     * @return List of scheduled medications
     *          (empty if no scheduled medications)
     */
    fun scheduledMedications(): List<Medication> =
        treatmentPlan.medications.filter { it.type == MedicationType.SCHEDULED }

    /**
     * Returns the as-needed medications.
     * @return List of as-needed medications
     *          (empty if no as-needed medications)
     */
    fun asNeededMedications(): List<Medication> =
        treatmentPlan.medications.filter { it.type == MedicationType.AS_NEEDED }
}
