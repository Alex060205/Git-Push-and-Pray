package com.example.vitala.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import java.time.LocalDate
import java.util.UUID

/**
 * A patient's profile.
 * @param id Unique identifier
 * @param name Name
 * @param birthDate Date of birth (String formatted, e.g. "YYYY-MM-DD")
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
    val name: String = "Patient",
    val birthDate: String = LocalDate.now().toString(),
    val gender: String = "",
    val heightCm: Double = 170.0,
    val weightKg: Double = 70.0,
    val affectedJoints: List<String> = listOf("Joint"),

    val activePlanId: String = UUID.randomUUID().toString(),
    val exercise: List<PrescribedExercise> = emptyList(),
    val medications: List<Medication> = emptyList(),

    val availability: AvailabilityPreferences = AvailabilityPreferences(),
) {
    @get:Exclude
    val bodyMetrics: BodyMetrics
        get() = BodyMetrics(heightCm, weightKg, affectedJoints)

    @get:Exclude
    val treatmentPlan: TreatmentPlan
        get() = TreatmentPlan(
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
