package com.example.vitala.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID


/** One medication dose. AS_NEEDED doses have no scheduledTime but record pain at that moment.
 * @param id Unique identifier
 * @param medicationId Id of the medication
 * @param date Date of the dose
 * @param scheduledTime Time of the dose
 * @param takenAt Time of taking
 * @param status Status of the dose
 * @param painLevelAtDose Pain level at the time of the dose
 * @constructor Creates a new medication log object with the given parameters
 * */
data class MedicationLog(
    val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val date: LocalDate,
    val scheduledTime: LocalTime? = null,
    val takenAt: LocalDateTime? = null,
    val status: DoseStatus = DoseStatus.PENDING,
    val painLevelAtDose: Int? = null
) {
    init {
        require(painLevelAtDose == null || painLevelAtDose in 0..10) { "Pain level must be 0-10" }
    }

    /**
     * Marks the dose as taken.
     * @param at Time of taking
     * @return A new medication log object with the given parameters
     *          and the status set to TAKEN.
     */
    fun markTaken(at: LocalDateTime) = copy(status = DoseStatus.TAKEN, takenAt = at)

    /**
     * Marks the dose as skipped. Notification of the dose will not be sent.
     * @return A new medication log object with the status set to SKIPPED.
     */
    fun markSkipped() = copy(status = DoseStatus.SKIPPED)

    /**
     * Marks the dose as snoozed. Notification of the dose will be sent later.
     * @return A new medication log object with the status set to SNOOZED.
     */
    fun snooze() = copy(status = DoseStatus.SNOOZED)
}