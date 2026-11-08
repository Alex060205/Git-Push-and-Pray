package com.example.vitala.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * One exercise occurrence: what was planned, and how it went.
 * @param id Unique identifier
 * @param patientId Patient this session belongs to
 * @param date Date of the exercise, "yyyy-MM-dd"
 * @param scheduledTime Time of the exercise, "HH:mm"
 * @param items Items in the exercise
 * @param status Status of the exercise
 * @param painBefore Pain before starting
 * @param painDuring Pain during the exercise
 * @param painAfter Pain after the exercise
 * @param symptomResponse Asked right after the session (DONE, OVERDONE or CUT_SHORT):
 *                        how it felt compared with what the doctor's description led them to expect
 * @param stopReason Why it was skipped or cut short
 */
data class ExerciseSession(
    @DocumentId val id: String = UUID.randomUUID().toString(),
    val patientId: String = "",
    val date: String = LocalDate.now().toString(),
    val scheduledTime: String = LocalTime.now().toString(),
    val items: List<ExerciseItem> = emptyList(),
    var status: SessionStatus = SessionStatus.PLANNED,
    var painBefore: PainFeeling? = null,
    var painDuring: PainFeeling? = null,
    var painAfter: PainFeeling? = null,
    var symptomResponse: SymptomResponse? = null,
    var stopReason: StopReason? = null
) {
    /** [date] as a LocalDate. Not stored in Firestore. */
    @get:Exclude
    val localDate: LocalDate get() = LocalDate.parse(date)

    /** [scheduledTime] as a LocalTime. Not stored in Firestore. */
    @get:Exclude
    val localTime: LocalTime get() = LocalTime.parse(scheduledTime)

    /** Items finished so far. Recomputed on each read; not stored in Firestore. */
    @get:Exclude
    val itemsCompleted: List<ExerciseItem> get() = items.filter { it.status == SessionStatus.DONE }

    /** Records pain before starting. Status stays PLANNED until the session ends. */
    fun recordPainBefore(before: PainFeeling) {
        this.painBefore = before
    }

    /**
     * Marks the exercise as done.
     * @param during Pain level during the exercise
     * @param after Pain level after the exercise
     * @param symptoms Symptom response asked right after the session
     */
    fun markDone(during: PainFeeling, after: PainFeeling, symptoms: SymptomResponse? = null) {
        this.status = SessionStatus.DONE
        this.painDuring = during
        this.painAfter = after
        this.symptomResponse = symptoms
        this.stopReason = null
    }

    /**
     * Marks the exercise as done, but more than prescribed.
     * @param during Pain level during the exercise
     * @param after Pain level after the exercise
     * @param symptoms Symptom response asked right after the session
     */
    fun markOverdone(during: PainFeeling, after: PainFeeling, symptoms: SymptomResponse? = null) {
        this.status = SessionStatus.OVERDONE
        this.painDuring = during
        this.painAfter = after
        this.symptomResponse = symptoms
        this.stopReason = null
    }

    /**
     * Marks the exercise as started but stopped early.
     * @param reason Why it was cut short
     */
    fun markCutShort(reason: StopReason, during: PainFeeling, after: PainFeeling, symptoms: SymptomResponse? = null) {
        this.status = SessionStatus.CUT_SHORT
        this.painDuring = during
        this.painAfter = after
        this.symptomResponse = symptoms
        this.stopReason = reason
    }

    /**
     * Marks the exercise as skipped.
     * @param reason Why, if the patient gave one
     */
    fun markSkipped(reason: StopReason? = null) {
        this.status = SessionStatus.SKIPPED
        this.stopReason = reason
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