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
 * @param painBefore Pain before starting
 * @param painDuring Pain during the exercise
 * @param painAfter Pain after the exercise
 * @param symptomResponse Asked right after the session (DONE or CUT_SHORT)
 * @param stopReason Why it was skipped or cut short
 */
data class ExerciseSession(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val scheduledTime: LocalTime,
    val intensity: Intensity,
    val items: List<ExerciseItem>,
    val status: SessionStatus = SessionStatus.PLANNED,
    val painBefore: PainFeeling? = null,
    val painDuring: PainFeeling? = null,
    val painAfter: PainFeeling? = null,
    val symptomResponse: SymptomResponse? = null,
    val stopReason: StopReason? = null
) : Notifiable {

    /** Records pain before starting. Status stays PLANNED until the session ends. */
    fun recordPainBefore(before: PainFeeling) = copy(painBefore = before)

    /** Marks the exercise as done. */
    fun markDone(during: PainFeeling, after: PainFeeling, symptoms: SymptomResponse? = null) =
        copy(status = SessionStatus.DONE, painDuring = during, painAfter = after,
            symptomResponse = symptoms, stopReason = null)

    /** Marks the exercise as started but stopped early, with the reason. */
    fun markCutShort(reason: StopReason, during: PainFeeling, after: PainFeeling, symptoms: SymptomResponse? = null) =
        copy(status = SessionStatus.CUT_SHORT, painDuring = during, painAfter = after,
            symptomResponse = symptoms, stopReason = reason)

    /** Marks the exercise as skipped, with the reason if one was given. */
    fun markSkipped(reason: StopReason? = null) = copy(status = SessionStatus.SKIPPED, stopReason = reason)

    /**
     * The key is tied to the session id, so moving the time replaces the old reminder instead of
     * adding a second one
     * @param date Date to check
     * @return List with one reminder, or empty if the date doesn't match or the session is
     * already done, cut short or skipped
     */
    override fun notificationsFor(date: LocalDate): List<NotificationRequest> {
        if (date != this.date || status != SessionStatus.PLANNED) return emptyList()
        return listOf(
            NotificationRequest(
                key = "session-$id",
                kind = NotificationKind.EXERCISE,
                title = when (intensity) {
                    Intensity.REST -> "Time for some light strethcing" //placeholders
                    Intensity.GENTLE -> "Time for your gentle exercises"
                    Intensity.FULL -> "Time for your exercise"
                },
                body = items.joinToString(",") { it.name }.ifEmpty { "Tap to start" },
                triggerAt = date.atTime(scheduledTime)
            )
        )
    }
}
