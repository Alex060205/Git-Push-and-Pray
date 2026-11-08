package com.example.vitala.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import kotlinx.coroutines.CoroutineScope
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * One exercise session: what was planned and how it went.
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
    var stopReason: StopReason? = null,
    var why: String = "",
    var symptomResponseText: String = ""
) {

    @get:Exclude
    val localDate: LocalDate
        get() = LocalDate.parse(date)

    @get:Exclude
    val localTime: LocalTime
        get() = LocalTime.parse(scheduledTime)

    // Current name used by main.
    @get:Exclude
    val itemsCompleted: List<ExerciseItem>
        get() = items.filter {
            it.status == SessionStatus.DONE
        }

    // Compatibility with existing callers.
    @get:Exclude
    val items_completed: List<ExerciseItem>
        get() = itemsCompleted

    fun recordPainBefore(before: PainFeeling) {
        painBefore = before
    }

    // Structured signature used by main.
    fun markDone(
        during: PainFeeling,
        after: PainFeeling,
        symptoms: SymptomResponse? = null
    ) {
        status = SessionStatus.DONE
        painDuring = during
        painAfter = after
        symptomResponse = symptoms
        stopReason = null
        why = ""
        symptomResponseText = ""
    }

    // Compatibility with Shen's signature.
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

    // Structured signature used by main.
    fun markOverdone(
        during: PainFeeling,
        after: PainFeeling,
        symptoms: SymptomResponse? = null
    ) {
        status = SessionStatus.OVERDONE
        painDuring = during
        painAfter = after
        symptomResponse = symptoms
        stopReason = null
        why = ""
        symptomResponseText = ""
    }

    // Compatibility with Shen's signature.
    fun markOverdone(
        before: PainFeeling,
        during: PainFeeling,
        after: PainFeeling,
        symptomResponse: String = "",
        why: String = ""
    ) {
        markOverdone(
            during = during,
            after = after,
            symptoms = parseSymptoms(symptomResponse)
        )

        painBefore = before
        symptomResponseText = symptomResponse
        stopReason = parseReason(why)
        this.why = why
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
        stopReason = reason
        why = ""
        symptomResponseText = ""
    }

    // Structured signature used by main.
    fun markSkipped(reason: StopReason? = null) {
        status = SessionStatus.SKIPPED
        stopReason = reason
        painDuring = null
        painAfter = null
        symptomResponse = null
        why = ""
        symptomResponseText = ""
    }

    // Compatibility with Shen's signature.
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
        val normalized = text.trim()

        return SymptomResponse.values().firstOrNull {
            it.name.equals(normalized, ignoreCase = true) ||
                    it.label.equals(normalized, ignoreCase = true)
        }
    }

    private fun parseReason(
        text: String
    ): StopReason? {
        val normalized = text.trim()

        return StopReason.values().firstOrNull {
            it.name.equals(normalized, ignoreCase = true) ||
                    it.label.equals(normalized, ignoreCase = true)
        }
    }
}