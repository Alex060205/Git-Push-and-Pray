<<<<<<< HEAD
package com.example.vitala.data
=======
package com.example.vitala
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

<<<<<<< HEAD
/**
 * The physician's prescription. Acts as the upper limit for any exercise adjustment.
 * @param id Unique identifier
 * @param physicianName Name of the physician
 * @param createdOn Date of creation
 * @param version Version of the plan
 * @param exercises List of exercises
 * @param medications List of medications
 * @param isActive Whether the plan is active
 * @constructor Creates a new treatment plan object with the given parameters
*/
=======
/** The physician's prescription. Acts as the upper limit for any exercise adjustment.
 *
 */
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
data class TreatmentPlan(
    val id: String = UUID.randomUUID().toString(),
    val physicianName: String,
    val createdOn: LocalDate,
    val version: Int = 1,
    val exercises: List<PrescribedExercise>,
    val medications: List<Medication>,
    val isActive: Boolean = true
) {
<<<<<<< HEAD
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
=======
    /**
     *
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
     */
    fun exercisesFor(date: LocalDate): List<PrescribedExercise> =
        exercises.filter { date.dayOfWeek in it.days }

<<<<<<< HEAD
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
=======
    fun scheduledMedications(): List<Medication> =
        medications.filter { it.type == MedicationType.SCHEDULED }

    fun asNeededMedications(): List<Medication> =
        medications.filter { it.type == MedicationType.AS_NEEDED }
}

/**
 * Rep-based (knee bends: sets = 2, reps = 10) or time-based (walk: durationMinutes = 15).
 */
data class PrescribedExercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val durationMinutes: Int? = null,
    val days: Set<DayOfWeek> = TimeBlock.EVERY_DAY,
    val instructions: String = ""
) {
    init {
        require((sets != null && reps != null) || durationMinutes != null) {
            "Exercise needs sets + reps or a duration"
        }
    }
}

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dose: String,                         // e.g. "1 tablet"
    val type: MedicationType,
    val times: List<LocalTime> = emptyList(), // only for SCHEDULED
    val notes: String = ""                    // e.g. "with food"
) {
    init {
        require(type == MedicationType.AS_NEEDED || times.isNotEmpty()) {
            "Scheduled medication needs at least one time"
        }
    }
>>>>>>> 1440c78383dfdaad572454e67eb916ff90aea6b5
}