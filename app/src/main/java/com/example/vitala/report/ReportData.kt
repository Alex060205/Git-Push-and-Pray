package com.example.vitala.report

import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.Patient
import com.example.vitala.data.SessionStatus
import java.time.Instant
import java.time.LocalDate

/** One rating for one day, 0-10. */
data class DailyRating(val date: LocalDate, val value: Int)

/** Why a session was skipped or cut short. Move into the data layer once ExerciseSession stores it. */
enum class StopReason(val label: String) {
    PAIN("Pain"), FATIGUE("Fatigue"), NO_TIME("No time"), OTHER("Other")
}

/** Asked immediately after each session. */
enum class SymptomResponse(val label: String) {
    WITHIN_RANGE("Familiar, within range"),
    BEYOND_RANGE("Familiar, beyond range"),
    NEW_OR_DIFFERENT("New or different")
}

/** Asked at the daily check-in while a change or flare is being evaluated. */
enum class RecoveryStatus(val label: String) {
    RETURNED("Back to usual range"), STILL_ABOVE("Still above usual range"), UNSURE("Unsure")
}

/** If recovered, roughly when. */
enum class RecoveryTime(val label: String) { SAME_DAY("same day"), NEXT_DAY("next day"), LATER("later") }

data class Recovery(val status: RecoveryStatus, val time: RecoveryTime? = null) {
    val label get() = if (status == RecoveryStatus.RETURNED && time != null) "${status.label} (${time.label})" else status.label
}

/**
 * Daily check-in items DailyCheckIn doesn't have yet. Fatigue and interference are 0-10;
 * interference is "with usual daily activities over the previous 24 hours".
 * Delete once these live on DailyCheckIn.
 */
data class DailyExtra(
    val date: LocalDate,
    val fatigue: Int? = null,
    val interference: Int? = null,
    val recovery: Recovery? = null
)

/** Weekly PEG: pain on average, interference with enjoyment of life, interference with general activity. Each 0-10. */
data class PegEntry(val date: LocalDate, val pain: Int, val enjoyment: Int, val activity: Int) {
    val score: Double get() = (pain + enjoyment + activity) / 3.0
}

/**
 * Session details ExerciseSession can't hold yet: cut short, the reason for a skip or cut,
 * and the symptom response. Keyed by session id. Delete once these live on ExerciseSession.
 */
data class SessionOutcome(
    val cutShort: Boolean = false,
    val reason: StopReason? = null,
    val symptoms: SymptomResponse? = null
)

enum class SessionResult(val label: String) {
    FINISHED("Finished"), CUT_SHORT("Cut short"), SKIPPED("Skipped"), NO_RECORD("No result recorded")
}

data class SessionRow(
    val session: ExerciseSession,
    val result: SessionResult,
    val reason: StopReason?,
    val symptoms: SymptomResponse?,
    /** From the session's items, or the plan for that weekday if the session has none. */
    val exerciseNames: List<String>
) {
    val incomplete get() = result != SessionResult.FINISHED
    val attempted get() = result == SessionResult.FINISHED || result == SessionResult.CUT_SHORT
}

/** Everything that goes in the doctor report. Nothing here is a clinical conclusion. */
data class ReportData(
    val generatedAt: Instant,
    val from: LocalDate,
    val to: LocalDate,
    val patient: Patient?,
    /** Pain from DailyCheckIn.painLevel, at most one per day, last 28 days. */
    val pain: List<DailyRating>,
    /** Fatigue, interference and recovery for the last 7 days. */
    val lastWeek: List<DailyExtra>,
    /** PEG questionnaires completed in the last 28 days, oldest first. */
    val peg: List<PegEntry>,
    val sessions: List<SessionRow>,
    val missingNotes: List<String>,
    /** Non-empty prints a SAMPLE DATA warning on the PDF. */
    val sampleNotes: List<String> = emptyList()
) {
    val weekFrom: LocalDate get() = to.minusDays(6)
    val finishedSessions get() = sessions.count { it.result == SessionResult.FINISHED }
    val recordedSessions get() = sessions.count { it.result != SessionResult.NO_RECORD }
    val incomplete get() = sessions.filter { it.result == SessionResult.SKIPPED || it.result == SessionResult.CUT_SHORT }

    companion object {
        const val DAYS = 28L

        fun build(
            today: LocalDate,
            now: Instant,
            patient: Patient?,
            checkIns: List<DailyCheckIn>,
            sessions: List<ExerciseSession>,
            daily: List<DailyExtra> = emptyList(),
            peg: List<PegEntry> = emptyList(),
            outcomes: Map<String, SessionOutcome> = emptyMap(),
            sampleNotes: List<String> = emptyList()
        ): ReportData {
            val from = today.minusDays(DAYS - 1)
            val range = from..today
            val week = today.minusDays(6)..today

            val pain = checkIns.filter { it.date in range }
                .groupBy { it.date }
                .map { (date, list) -> DailyRating(date, list.last().painLevel) }
                .sortedBy { it.date }

            val lastWeek = daily.filter { it.date in week }
                .groupBy { it.date }.map { it.value.last() }
                .sortedBy { it.date }

            val pegInRange = peg.filter { it.date in range }.sortedBy { it.date }

            val rows = sessions
                .filter { it.date in range }
                // Today's or future planned sessions aren't missing yet.
                .filterNot { it.status == SessionStatus.PLANNED && !it.date.isBefore(today) }
                .sortedWith(compareBy<ExerciseSession>({ it.date }, { it.scheduledTime }))
                .map { s ->
                    val outcome = outcomes[s.id]
                    val result = when (s.status) {
                        SessionStatus.SKIPPED -> SessionResult.SKIPPED
                        SessionStatus.DONE -> if (outcome?.cutShort == true) SessionResult.CUT_SHORT else SessionResult.FINISHED
                        SessionStatus.PLANNED -> SessionResult.NO_RECORD
                    }
                    val names = s.items.map { it.name }
                        .ifEmpty { patient?.exercisesFor(s.date)?.map { it.name }.orEmpty() }
                    SessionRow(s, result, outcome?.reason, outcome?.symptoms, names)
                }

            val missing = buildList {
                val noCheckIn = DAYS - pain.size
                if (pain.isEmpty()) add("No daily check-ins were recorded in the last 28 days.")
                else if (noCheckIn > 0) add("No daily check-in on $noCheckIn of the 28 days.")

                val fatigueDays = lastWeek.count { it.fatigue != null }
                if (fatigueDays < 7) add("Fatigue was rated on $fatigueDays of the last 7 days.")
                val interferenceDays = lastWeek.count { it.interference != null }
                if (interferenceDays < 7) add("Interference with daily activities was rated on $interferenceDays of the last 7 days.")

                if (pegInRange.isEmpty()) add("No PEG questionnaire was completed in the last 28 days.")
                else if (pegInRange.size < 4) add("The weekly PEG questionnaire was completed ${pegInRange.size} times in 4 weeks.")

                if (rows.isEmpty()) add("No exercise sessions were recorded in the last 28 days.")
                val noRecord = rows.count { it.result == SessionResult.NO_RECORD }
                if (noRecord > 0) add("$noRecord past sessions were never marked done or skipped.")
                val noReason = rows.count {
                    (it.result == SessionResult.SKIPPED || it.result == SessionResult.CUT_SHORT) && it.reason == null
                }
                if (noReason > 0) add("No reason was recorded for $noReason skipped or cut-short sessions.")
                val noPainAfter = rows.count { it.attempted && it.session.painAfter == null }
                if (noPainAfter > 0) add("Pain after exercise was not recorded for $noPainAfter sessions.")
                val noSymptoms = rows.count { it.attempted && it.symptoms == null }
                if (noSymptoms > 0) add("Symptom response was not recorded for $noSymptoms sessions.")
            }

            return ReportData(
                generatedAt = now, from = from, to = today, patient = patient,
                pain = pain, lastWeek = lastWeek, peg = pegInRange, sessions = rows,
                missingNotes = missing, sampleNotes = sampleNotes
            )
        }
    }
}
