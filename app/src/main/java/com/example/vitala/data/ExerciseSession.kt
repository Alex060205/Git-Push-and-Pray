package com.example.vitala.data

import com.google.firebase.firestore.DocumentId
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * One exercise occurrence: what was planned, and how it went.
 * @param id Unique identifier
 * @param date Date of the exercise
 * @param scheduledTime Time of the exercise
 * @param items Items in the exercise
 * @param status Status of the exercise
 * @param painDuring Pain level during the exercise
 * @param painAfter Pain level after the exercise
 * @constructor Creates a new exercise session object with the given parameters
 */
data class ExerciseSession(
    @DocumentId val id: String = UUID.randomUUID().toString(),
    val patientId: String = "",
    val date: String = LocalDate.now().toString(),
    val scheduledTime: String = LocalTime.now().toString(),
    val items: List<ExerciseItem>,
    var status: SessionStatus = SessionStatus.PLANNED,
    var painDuring: PainFeeling? = null,
    var painAfter: PainFeeling? = null
) {
    /**
     * Marks the exercise as done.
     * @param during Pain level during the exercise
     * @param after Pain level after the exercise
     */
    fun markDone(during: PainFeeling, after: PainFeeling) {
        this.status = SessionStatus.DONE
        this.painDuring = during
        this.painAfter = after
    }

    /**
     * Marks the exercise as skipped.
     */
    fun markSkipped() {
        this.status = SessionStatus.SKIPPED
    }

    /**
     * Starts the exercise items with a coroutine scope.
     */
    fun start(scope: CoroutineScope, onTick: (ExerciseItem, Long) -> Unit = { _, _ -> }) {
        for (item in items) {
            item.start(scope) { seconds ->
                onTick(item, seconds)
            }
        }
    }
    /**
     * Stops the exercise items.
     */
    fun stop() {
        for (item in items) {
            item.stop()
        }
    }
}
