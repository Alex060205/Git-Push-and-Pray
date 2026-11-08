package com.example.vitala.report

import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.HealthLogRepository
import com.example.vitala.data.PainFeeling
import com.example.vitala.data.Patient
import com.example.vitala.data.PegResponse
import com.example.vitala.data.Recovery
import com.example.vitala.data.RecoveryStatus
import com.example.vitala.data.RecoveryTime
import com.example.vitala.data.SessionStatus
import com.example.vitala.data.StopReason
import com.example.vitala.data.SymptomResponse
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * TEMPORARY. Made-up check-ins, sessions and PEG answers for testing the report, either directly
 * ([build]) or through Firestore ([upload] then HealthLogRepository.loadReport). Delete when done testing.
 *
 * Anchored on 2026-11-08 (28 days from 2026-10-12). Pass another `today` and every date shifts with it.
 */
object ReportSample {
    val ANCHOR: LocalDate = LocalDate.of(2026, 11, 8)

    const val WARNING = "SAMPLE DATA: daily check-ins, exercise sessions and PEG answers in this report are made-up " +
            "placeholders for testing. Only the patient profile and prescribed exercises are real."

    // Oct 12 .. Nov 8, one value per day. Flares around Oct 17-18 and Oct 29-31, easing in the last week.
    private val PAIN = intArrayOf(
        7, 5, 6, 7, 5, 7, 9,   6, 5, 5, 7, 5, 6, 5,   5, 6, 5, 7, 8, 7, 5,   4, 5, 3, 4, 5, 5, 5
    )
    private val FATIGUE = intArrayOf(
        6, 5, 5, 6, 4, 6, 8,   5, 4, 5, 6, 4, 5, 4,   4, 5, 5, 6, 7, 7, 5,   5, 7, 4, 4, 3, 5, 5
    )
    private val INTERFERENCE = intArrayOf(
        5, 4, 4, 5, 3, 5, 8,   4, 3, 4, 5, 3, 4, 3,   3, 4, 4, 6, 7, 6, 4,   4, 6, 3, 3, 2, 4, 3
    )

    /** Day index (0 = Oct 12) -> recovery answer, asked only while a flare or change was evaluated. */
    private val RECOVERY = mapOf(
        7 to Recovery(RecoveryStatus.STILL_ABOVE),                     // Oct 19
        8 to Recovery(RecoveryStatus.RETURNED, RecoveryTime.LATER),    // Oct 20
        21 to Recovery(RecoveryStatus.STILL_ABOVE),                    // Nov 2
        22 to Recovery(RecoveryStatus.UNSURE),                         // Nov 3
        23 to Recovery(RecoveryStatus.RETURNED, RecoveryTime.LATER),   // Nov 4
        25 to Recovery(RecoveryStatus.RETURNED, RecoveryTime.NEXT_DAY) // Nov 6
    )

    private data class Sample(
        val month: Int, val day: Int, val status: SessionStatus,
        val before: PainFeeling? = null, val during: PainFeeling? = null, val after: PainFeeling? = null,
        val symptoms: SymptomResponse? = null, val reason: StopReason? = null
    )

    private val NONE = PainFeeling.NONE
    private val SOME = PainFeeling.SOME
    private val A_LOT = PainFeeling.A_LOT
    private val WITHIN = SymptomResponse.WITHIN_RANGE
    private val BEYOND = SymptomResponse.BEYOND_RANGE
    private val NEW = SymptomResponse.NEW_OR_DIFFERENT

    /** Mon / Wed / Fri / Sat sessions. 10 finished, 3 cut short, 3 skipped. */
    private val SESSIONS = listOf(
        Sample(10, 12, SessionStatus.DONE, SOME, SOME, SOME, WITHIN),
        Sample(10, 14, SessionStatus.DONE, SOME, SOME, SOME, WITHIN),
        Sample(10, 16, SessionStatus.DONE, SOME, SOME, NONE, WITHIN),
        Sample(10, 17, SessionStatus.CUT_SHORT, SOME, A_LOT, A_LOT, BEYOND, StopReason.PAIN),
        Sample(10, 19, SessionStatus.DONE, SOME, NONE, NONE, WITHIN),
        Sample(10, 21, SessionStatus.DONE, SOME, SOME, SOME, WITHIN),
        Sample(10, 23, SessionStatus.DONE, SOME, SOME, SOME, NEW),
        Sample(10, 24, SessionStatus.DONE, NONE, NONE, NONE, WITHIN),
        Sample(10, 26, SessionStatus.SKIPPED, reason = StopReason.NO_TIME),
        Sample(10, 28, SessionStatus.SKIPPED, reason = StopReason.FATIGUE),
        Sample(10, 30, SessionStatus.CUT_SHORT, A_LOT, A_LOT, A_LOT, BEYOND, StopReason.PAIN),
        Sample(10, 31, SessionStatus.SKIPPED, reason = StopReason.PAIN),
        Sample(11, 2, SessionStatus.DONE, SOME, NONE, NONE, WITHIN),
        Sample(11, 4, SessionStatus.CUT_SHORT, NONE, SOME, SOME, WITHIN, StopReason.FATIGUE),
        Sample(11, 6, SessionStatus.DONE, NONE, NONE, NONE, WITHIN),
        Sample(11, 7, SessionStatus.DONE, SOME, NONE, SOME, WITHIN)
    )

    /** Weekly PEG on Sundays (Oct 18, 25, Nov 1, 8): pain, enjoyment, general activity. */
    private val PEG = listOf(Triple(7, 6, 7), Triple(6, 5, 5), Triple(7, 6, 6), Triple(5, 4, 4))

    data class Records(
        val checkIns: List<DailyCheckIn>,
        val sessions: List<ExerciseSession>,
        val peg: List<PegResponse>
    )

    fun records(today: LocalDate = ANCHOR): Records {
        val shift = ChronoUnit.DAYS.between(ANCHOR, today)

        val checkIns = PAIN.indices.map { i ->
            DailyCheckIn(
                date = today.minusDays(27L - i),
                painLevel = PAIN[i],
                fatigue = FATIGUE[i],
                interference = INTERFERENCE[i],
                recovery = RECOVERY[i]
            )
        }

        val sessions = SESSIONS.mapIndexed { i, s ->
            ExerciseSession(
                id = "sample-session-$i",
                date = LocalDate.of(2026, s.month, s.day).plusDays(shift).toString(),
                scheduledTime = if (i % 2 == 0) "09:00" else "18:00",
                items = emptyList(),
                status = s.status,
                painBefore = s.before,
                painDuring = s.during,
                painAfter = s.after,
                symptomResponse = s.symptoms,
                stopReason = s.reason
            )
        }

        val peg = PEG.mapIndexed { i, (p, e, g) -> PegResponse(today.minusDays(21L - 7L * i), p, e, g) }

        return Records(checkIns, sessions, peg)
    }

    /** Builds the report straight from the sample records, without Firestore. */
    fun build(patient: Patient?, today: LocalDate = ANCHOR, now: Instant = Instant.now()): ReportData {
        val r = records(today)
        return ReportData.build(today, now, patient, r.checkIns, r.sessions, r.peg, sampleNotes = listOf(WARNING))
    }

    /** Writes the sample records to Firestore under [patientId]. Safe to run again: same documents are overwritten. */
    suspend fun upload(repo: HealthLogRepository, patientId: String, today: LocalDate = ANCHOR) {
        val r = records(today)
        r.checkIns.forEach { repo.saveCheckIn(patientId, it) }
        r.sessions.forEach { repo.saveSession(patientId, it.copy(patientId = patientId)) }
        r.peg.forEach { repo.savePeg(patientId, it) }
    }
}