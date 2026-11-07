package com.example.vitala.data

import java.time.LocalTime
import java.util.UUID


/**
 * A medication.
 * @param id Unique identifier
 * @param name Name of the medication
 * @param dose Dosage of the medication
 *  @param type Type of the medication
 *  @param times Times of the medication
 *  @param notes Notes about the medication
 *  @constructor Creates a new medication object with the given parameters
 */
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
}