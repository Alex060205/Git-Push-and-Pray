package com.example.vitala.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

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

    // Structured answers used by main's reports and Firebase mapping.
    var symptomResponse: SymptomResponse? = null,
    var stopReason: StopReason? = null,

    // Original text retained for compatibility with Shen's methods.
    var why: String = "",
    var symptomResponseText: String = ""
) {

    @get:Exclude
    val localDate: LocalDate
        get() = LocalDate.parse(date)

    @get:Exclude
    val localTime: LocalTime
        get() = LocalTime.parse(scheduledTime)

    @get:Exclude
    val items_completed: List<ExerciseItem>
        get() = items.filter {
            it.status == SessionStatus.DONE
        }

    fun recordPainBefore(before: PainFeeling) {
        painBefore = before
    }

    // Main's signature.
    fun markDone(
        during: PainFeeling,
        after: PainFeeling,
        symptoms: SymptomResponse? = null
    ) {
        status = SessionStatus.DONE
        painDuring = during
        painAfter = after
        symptomResponse = symptoms
        symptomResponseText = ""
        stopReason = null
        why = ""
    }

    // Shen's signature.
    fun markDone(
        before: PainFeeling,
        during: PainFeeling,
        after: PainFeeling,
        symptomResponse: String = ""
    ) {
        markDone(
            during = during,
            after = after,
            symptoms = parseSymptoms(symptomResponse)
        )

        painBefore = before
        symptomResponseText = symptomResponse
    }

    fun markCutShort(
        reason: StopReason,
        during: PainFeeling,
        after: PainFeeling,
        symptoms: SymptomResponse? = null
    ) {
        status = SessionStatus.CUT_SHORT
        painDuring = during
        painAfter = after
        symptomResponse = symptoms
        symptomResponseText = ""
        stopReason = reason
        why = ""
    }

    // Main's signature.
    fun markSkipped(reason: StopReason? = null) {
        status = SessionStatus.SKIPPED
        stopReason = reason
        painDuring = null
        painAfter = null
        symptomResponse = null
        symptomResponseText = ""
        why = ""
    }

    // Shen's signature.
    fun markSkipped(
        before: PainFeeling,
        why: String = ""
    ) {
        markSkipped(reason = parseReason(why))

        painBefore = before
        this.why = why
    }

    fun markIncompleted(
        before: PainFeeling,
        during: PainFeeling,
        after: PainFeeling,
        why: String = ""
    ) {
        status = SessionStatus.INCOMPLETE
        painBefore = before
        painDuring = during
        painAfter = after
        stopReason = parseReason(why)
        symptomResponse = null
        symptomResponseText = ""
        this.why = why
    }

    fun markOverdone(
        before: PainFeeling,
        during: PainFeeling,
        after: PainFeeling,
        symptomResponse: String = "",
        why: String = ""
    ) {
        status = SessionStatus.OVERDONE
        painBefore = before
        painDuring = during
        painAfter = after
        this.symptomResponse = parseSymptoms(symptomResponse)
        symptomResponseText = symptomResponse
        stopReason = parseReason(why)
        this.why = why
    }

    fun start(
        scope: CoroutineScope,
        onTick: (ExerciseItem, Long) -> Unit = { _, _ -> }
    ) {
        for (item in items) {
            item.start(scope) { seconds ->
                onTick(item, seconds)
            }
        }
    }

    fun stop() {
        for (item in items) {
            item.stop()
        }
    }

    private fun parseSymptoms(
        text: String
    ): SymptomResponse? {
        return SymptomResponse.values().firstOrNull {
            it.name.equals(
                text.trim(),
                ignoreCase = true
            ) || it.label.equals(
                text.trim(),
                ignoreCase = true
            )
        }
    }

    private fun parseReason(
        text: String
    ): StopReason? {
        return StopReason.values().firstOrNull {
            it.name.equals(
                text.trim(),
                ignoreCase = true
            ) || it.label.equals(
                text.trim(),
                ignoreCase = true
            )
        }
    }
}