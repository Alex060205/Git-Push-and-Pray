package com.example.vitala.data

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID


/**
 * One exercise occurrence: what was planned, and how it went.
 * @param id Unique identifier
 * @param date Date of the exercise
 * @param scheduledTime Time of the exercise
 * @param intensity Intensity of the exercise
 * @param items Items in the exercise
 * @param status Status of the exercise
 * @param painDuring Pain level during the exercise
 * @param painAfter Pain level after the exercise
 * @constructor Creates a new exercise session object with the given parameters
 */
data class ExerciseSession(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val scheduledTime: LocalTime,
    val intensity: Intensity,
    val items: List<ExerciseItem>,
    val status: SessionStatus = SessionStatus.PLANNED,
    val painDuring: PainFeeling? = null,
    val painAfter: PainFeeling? = null
) {
    /**
     * Marks the exercise as done.
     * @param during Pain level during the exercise
     * @param after Pain level after the exercise
     * @return A new exercise session object with the given parameters
     *          and the status set to DONE.
     */
    fun markDone(during: PainFeeling, after: PainFeeling) =
        copy(status = SessionStatus.DONE, painDuring = during, painAfter = after)

    /**
     * Marks the exercise as skipped.
     * @return A new exercise session object with the status set to SKIPPED.
     */
    fun markSkipped() = copy(status = SessionStatus.SKIPPED)
}