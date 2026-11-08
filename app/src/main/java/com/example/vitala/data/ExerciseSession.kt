package com.example.vitala.data

import android.health.connect.datatypes.SymptomRecord
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
    var painBefore: PainFeeling? = null,
    var painDuring: PainFeeling? = null,
    var painAfter: PainFeeling? = null,
    var why: String = "",
    var symptomResponse: String = ""
) {
    val items_completed: List<ExerciseItem> = items.filter { it.status == SessionStatus.DONE }

    fun markOverdone(before: PainFeeling, during: PainFeeling, after: PainFeeling, symptomResponse: String = "", why: String = "") {
        this.status = SessionStatus.OVERDONE
        this.painBefore = before
        this.painDuring = during
        this.painAfter = after
        this.symptomResponse = symptomResponse
        this.why = why
    }

    /**
     * Marks the exercise as done.
     * @param before Pain level before the exercise
     * @param during Pain level during the exercise
     * @param after Pain level after the exercise
     * @param symptomResponse Symptom response, how much did you feel during the exercise compared to
     * to what you expected to feel from the description of the doctors?
     */
    fun markDone(before: PainFeeling, during: PainFeeling, after: PainFeeling, symptomResponse: String = "") {
        this.status = SessionStatus.DONE
        this.painBefore = before
        this.painDuring = during
        this.painAfter = after
        this.symptomResponse = symptomResponse
    }

    /**
     * Marks the exercise as skipped.
     * @param before Pain level before the exercise
     * @param why Why the exercise was skipped
     */
    fun markSkipped(before: PainFeeling, why: String = "") {
        this.status = SessionStatus.SKIPPED
        this.painBefore = before
        this.why = why
    }

    /**
     * Marks the exercise as incomplete.
     * @param before Pain level before the exercise
     * @param during Pain level during the exercise
     * @param after Pain level after the exercise
     * @param why Why the exercise was incomplete
     * @param symptomResponse Symptom response, how much did you feel during the exercise compared to
     */
    fun markIncompleted(before: PainFeeling, during: PainFeeling, after: PainFeeling, why: String = "") {
        this.status = SessionStatus.INCOMPLETE
        this.painBefore = before
        this.painDuring = during
        this.painAfter = after
        this.why = why
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
