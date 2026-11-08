package com.example.vitala.data

import com.google.firebase.firestore.DocumentId
import java.time.LocalDate
import java.util.UUID

/**
 * The physician's prescription. Acts as the upper limit for any exercise adjustment.
 * @param id Unique identifier
 * @param physicianName Name of the physician
 * @param createdOn Date of creation
 * @param exercises List of exercises
 * @param medications List of medications
 * @param isActive Whether the plan is active
 * @constructor Creates a new treatment plan object with the given parameters
 */
data class TreatmentPlan(
    @DocumentId val id: String = UUID.randomUUID().toString(),
    val physicianName: String = "",
    val createdOn: LocalDate = LocalDate.now(),
    val exercises: List<PrescribedExercise> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val isActive: Boolean = true
) {
    init {
        require(exercises.isNotEmpty() || medications.isNotEmpty()) {
            "Plan needs exercises or medications"
        }
    }

    /**
     * Returns the exercises that apply to the given date.
     * @param date Date to check
     * @return List of exercises that apply to the given date
     *          (empty if no exercises apply)
     */
    fun exercisesFor(date: LocalDate): List<PrescribedExercise> =
        exercises.filter { date.dayOfWeek in it.days }

    /**
     * Returns the scheduled medications.
     * @return List of scheduled medications
     *          (empty if no scheduled medications)
     */
    fun scheduledMedications(): List<Medication> =
        medications.filter { it.type == MedicationType.SCHEDULED }

    /**
     * Returns the as-needed medications.
     * @return List of as-needed medications
     *          (empty if no as-needed medications)
     */
    fun asNeededMedications(): List<Medication> =
        medications.filter { it.type == MedicationType.AS_NEEDED }
}
