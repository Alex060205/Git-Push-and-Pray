package com.example.vitala.report

import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.HealthLogRepository
import com.example.vitala.data.Patient
import com.example.vitala.data.PegResponse
import com.example.vitala.data.SessionStatus
import com.example.vitala.data.StopReason
import com.example.vitala.data.SymptomResponse
import java.time.Instant
import java.time.LocalDate

data class DailyRating(
    val date: LocalDate,
    val value: Int
)

enum class SessionResult(val label: String) {
    FINISHED("Finished"),
    OVERDONE("Did more than planned"),
    CUT_SHORT("Cut short"),
    SKIPPED("Skipped"),
    NO_RECORD("No result recorded")
}

data class SessionRow(
    val session: ExerciseSession,
    val result: SessionResult,
    val exerciseNames: List<String>
) {
    val reason: StopReason?
        get() = session.stopReason

    val symptoms: SymptomResponse?
        get() = session.symptomResponse

    val incomplete: Boolean
        get() = result != SessionResult.FINISHED &&
                result != SessionResult.OVERDONE

    val attempted: Boolean
        get() = result == SessionResult.FINISHED ||
                result == SessionResult.OVERDONE ||
                result == SessionResult.CUT_SHORT
}

/**
 * Reporting data only.
 * These summaries do not determine exercise progression.
 */
data class ReportData(
    val generatedAt: Instant,
    val from: LocalDate,
    val to: LocalDate,
    val patient: Patient?,
    val pain: List<DailyRating>,
    val lastWeek: List<DailyCheckIn>,
    val peg: List<PegResponse>,
    val sessions: List<SessionRow>,
    val missingNotes: List<String>,
    val sampleNotes: List<String> = emptyList()
) {
    val weekFrom: LocalDate
        get() = to.minusDays(6)

    val finishedSessions: Int
        get() = sessions.count {
            it.result == SessionResult.FINISHED ||
                    it.result == SessionResult.OVERDONE
        }

    val overdoneSessions: Int
        get() = sessions.count {
            it.result == SessionResult.OVERDONE
        }

    val recordedSessions: Int
        get() = sessions.count {
            it.result != SessionResult.NO_RECORD
        }

    val incomplete: List<SessionRow>
        get() = sessions.filter {
            it.result == SessionResult.SKIPPED ||
                    it.result == SessionResult.CUT_SHORT
        }

    companion object {
        const val DAYS = 28L

        fun build(
            today: LocalDate,
            now: Instant,
            patient: Patient?,
            checkIns: List<DailyCheckIn>,
            sessions: List<ExerciseSession>,
            peg: List<PegResponse> = emptyList(),
            sampleNotes: List<String> = emptyList()
        ): ReportData {
            val from = today.minusDays(DAYS - 1)
            val range = from..today
            val week = today.minusDays(6)..today

            val daily = checkIns
                .filter { it.date in range }
                .groupBy { it.date }
                .map { it.value.last() }
                .sortedBy { it.date }

            val pain = daily.map {
                DailyRating(it.date, it.painLevel)
            }

            val lastWeek = daily.filter {
                it.date in week
            }

            val pegInRange = peg
                .filter { it.date in range }
                .sortedBy { it.date }

            val rows = sessions
                .filter { it.localDate in range }
                .filterNot {
                    it.status == SessionStatus.PLANNED &&
                            !it.localDate.isBefore(today)
                }
                .sortedWith(
                    compareBy<ExerciseSession>(
                        { it.localDate },
                        { it.localTime }
                    )
                )
                .map { session ->
                    val result = when (session.status) {
                        SessionStatus.DONE ->
                            SessionResult.FINISHED

                        SessionStatus.OVERDONE ->
                            SessionResult.OVERDONE

                        SessionStatus.CUT_SHORT,
                        SessionStatus.INCOMPLETE ->
                            SessionResult.CUT_SHORT

                        SessionStatus.SKIPPED ->
                            SessionResult.SKIPPED

                        SessionStatus.PLANNED ->
                            SessionResult.NO_RECORD
                    }

                    val names = session.items
                        .map { it.name }
                        .ifEmpty {
                            patient
                                ?.exercisesFor(session.localDate)
                                ?.map { it.name }
                                .orEmpty()
                        }

                    SessionRow(
                        session = session,
                        result = result,
                        exerciseNames = names
                    )
                }

            val missing = buildList {
                val noCheckIn = DAYS - daily.size

                if (daily.isEmpty()) {
                    add(
                        "No daily check-ins were recorded " +
                                "in the last 28 days."
                    )
                } else if (noCheckIn > 0) {
                    add(
                        "No daily check-in on " +
                                "$noCheckIn of the 28 days."
                    )
                }

                if (lastWeek.size < 7) {
                    add(
                        "Fatigue and interference were rated on " +
                                "${lastWeek.size} of the last 7 days."
                    )
                }

                if (pegInRange.isEmpty()) {
                    add(
                        "No PEG questionnaire was completed " +
                                "in the last 28 days."
                    )
                } else if (pegInRange.size < 4) {
                    add(
                        "The weekly PEG questionnaire was completed " +
                                "${pegInRange.size} times in 4 weeks."
                    )
                }

                if (rows.isEmpty()) {
                    add(
                        "No exercise sessions were recorded " +
                                "in the last 28 days."
                    )
                }

                val noRecord = rows.count {
                    it.result == SessionResult.NO_RECORD
                }

                if (noRecord > 0) {
                    add(
                        "$noRecord past sessions have " +
                                "no recorded outcome."
                    )
                }

                val noReason = rows.count {
                    (
                            it.result == SessionResult.SKIPPED ||
                                    it.result == SessionResult.CUT_SHORT
                            ) && it.reason == null
                }

                if (noReason > 0) {
                    add(
                        "No structured reason was recorded for " +
                                "$noReason skipped or cut-short sessions."
                    )
                }

                val noPainBefore = rows.count {
                    it.attempted && it.session.painBefore == null
                }

                if (noPainBefore > 0) {
                    add(
                        "Pain before exercise was not recorded " +
                                "for $noPainBefore sessions."
                    )
                }

                val noPainAfter = rows.count {
                    it.attempted && it.session.painAfter == null
                }

                if (noPainAfter > 0) {
                    add(
                        "Pain after exercise was not recorded " +
                                "for $noPainAfter sessions."
                    )
                }

                val noSymptoms = rows.count {
                    it.attempted && it.symptoms == null
                }

                if (noSymptoms > 0) {
                    add(
                        "Structured symptom response was not recorded " +
                                "for $noSymptoms sessions."
                    )
                }

                val overdoneCount = rows.count {
                    it.result == SessionResult.OVERDONE
                }

                if (overdoneCount > 0) {
                    add(
                        "$overdoneCount sessions were marked " +
                                "as more than planned."
                    )
                }

                rows.forEach { row ->
                    val session = row.session

                    if (session.why.isNotBlank()) {
                        add(
                            "${session.date} ${session.scheduledTime}: " +
                                    "original reason: ${session.why}"
                        )
                    }

                    if (session.symptomResponseText.isNotBlank()) {
                        add(
                            "${session.date} ${session.scheduledTime}: " +
                                    "original symptom response: " +
                                    session.symptomResponseText
                        )
                    }
                }
            }

            return ReportData(
                generatedAt = now,
                from = from,
                to = today,
                patient = patient,
                pain = pain,
                lastWeek = lastWeek,
                peg = pegInRange,
                sessions = rows,
                missingNotes = missing,
                sampleNotes = sampleNotes
            )
        }
    }
}

suspend fun HealthLogRepository.loadReport(
    patient: Patient,
    today: LocalDate = LocalDate.now(),
    sampleNotes: List<String> = emptyList()
): ReportData {
    val from = today.minusDays(ReportData.DAYS - 1)

    return ReportData.build(
        today = today,
        now = Instant.now(),
        patient = patient,
        checkIns = checkIns(patient.id, from, today),
        sessions = sessions(patient.id, from, today),
        peg = peg(patient.id, from, today),
        sampleNotes = sampleNotes
    )
}