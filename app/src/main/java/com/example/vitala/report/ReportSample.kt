package com.example.vitala.report

import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.Intensity
import com.example.vitala.data.Mood
import com.example.vitala.data.Nutrition
import com.example.vitala.data.PainDuration
import com.example.vitala.data.PainFeeling
import com.example.vitala.data.Patient
import com.example.vitala.data.SessionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * TEMPORARY. Made-up check-in, session and PEG data for a real [Patient], so the PDF can be built
 * before Firebase has those records. Delete this file once ReportData.build is fed real data.
 *
 * Anchored on 2026-11-08 (28 days from 2026-10-12). Pass another `today` and every date shifts with it.
 */
object ReportSample {
    val ANCHOR: LocalDate = LocalDate.of(2026, 11, 8)

    /** Daily pain 0-10, Oct 12 .. Nov 8. Flares around Oct 17-18 and Oct 29-31, easing in the last week. */
    private val PAIN = intArrayOf(
        7, 5, 6, 7, 5, 7, 9,   // Oct 12-18
        6, 5, 5, 7, 5, 6, 5,   // Oct 19-25
        5, 6, 5, 7, 8, 7, 5,   // Oct 26-Nov 1
        4, 5, 3, 4, 5, 5, 5    // Nov 2-8
    )

    /** Nov 2 .. Nov 8: fatigue, interference, recovery (asked while the Oct 29-31 flare and the Nov 4 cut were evaluated). */
    private val FATIGUE = intArrayOf(5, 7, 4, 4, 3, 5, 5)
    private val INTERFERENCE = intArrayOf(4, 6, 3, 3, 2, 4, 3)
    private val RECOVERY = listOf(
        Recovery(RecoveryStatus.STILL_ABOVE),
        Recovery(RecoveryStatus.UNSURE),
        Recovery(RecoveryStatus.RETURNED, RecoveryTime.LATER),
        null,
        Recovery(RecoveryStatus.RETURNED, RecoveryTime.NEXT_DAY),
        null,
        null
    )

    private data class Sample(
        val month: Int, val day: Int, val status: SessionStatus,
        val during: PainFeeling? = null, val after: PainFeeling? = null,
        val symptoms: SymptomResponse? = null,
        val cutShort: Boolean = false, val reason: StopReason? = null
    )

    private val WITHIN = SymptomResponse.WITHIN_RANGE
    private val BEYOND = SymptomResponse.BEYOND_RANGE
    private val NEW = SymptomResponse.NEW_OR_DIFFERENT

    /** Mon / Wed / Fri / Sat sessions. 10 finished, 3 cut short, 3 skipped. */
    private val SESSIONS = listOf(
        Sample(10, 12, SessionStatus.DONE, PainFeeling.SOME, PainFeeling.SOME, WITHIN),
        Sample(10, 14, SessionStatus.DONE, PainFeeling.SOME, PainFeeling.SOME, WITHIN),
        Sample(10, 16, SessionStatus.DONE, PainFeeling.SOME, PainFeeling.NONE, WITHIN),
        Sample(10, 17, SessionStatus.DONE, PainFeeling.A_LOT, PainFeeling.A_LOT, BEYOND, cutShort = true, reason = StopReason.PAIN),
        Sample(10, 19, SessionStatus.DONE, PainFeeling.NONE, PainFeeling.NONE, WITHIN),
        Sample(10, 21, SessionStatus.DONE, PainFeeling.SOME, PainFeeling.SOME, WITHIN),
        Sample(10, 23, SessionStatus.DONE, PainFeeling.SOME, PainFeeling.SOME, NEW),
        Sample(10, 24, SessionStatus.DONE, PainFeeling.NONE, PainFeeling.NONE, WITHIN),
        Sample(10, 26, SessionStatus.SKIPPED, reason = StopReason.NO_TIME),
        Sample(10, 28, SessionStatus.SKIPPED, reason = StopReason.FATIGUE),
        Sample(10, 30, SessionStatus.DONE, PainFeeling.A_LOT, PainFeeling.A_LOT, BEYOND, cutShort = true, reason = StopReason.PAIN),
        Sample(10, 31, SessionStatus.SKIPPED, reason = StopReason.PAIN),
        Sample(11, 2, SessionStatus.DONE, PainFeeling.NONE, PainFeeling.NONE, WITHIN),
        Sample(11, 4, SessionStatus.DONE, PainFeeling.SOME, PainFeeling.SOME, WITHIN, cutShort = true, reason = StopReason.FATIGUE),
        Sample(11, 6, SessionStatus.DONE, PainFeeling.NONE, PainFeeling.NONE, WITHIN),
        Sample(11, 7, SessionStatus.DONE, PainFeeling.NONE, PainFeeling.SOME, WITHIN)
    )

    /** Weekly PEG on Sundays: pain, enjoyment, general activity. */
    private val PEG = listOf(
        Triple(7, 6, 7),   // Oct 18
        Triple(6, 5, 5),   // Oct 25
        Triple(7, 6, 6),   // Nov 1
        Triple(5, 4, 4)    // Nov 8
    )

    const val WARNING = "SAMPLE DATA: daily check-ins, exercise sessions and PEG answers in this report are made-up " +
            "placeholders for testing. Only the patient profile and prescribed exercises are real."

    fun build(patient: Patient?, today: LocalDate = ANCHOR, now: Instant = Instant.now()): ReportData {
        val shift = ChronoUnit.DAYS.between(ANCHOR, today)

        val checkIns = PAIN.mapIndexed { i, p ->
            DailyCheckIn(
                id = "sample-checkin-$i",
                date = today.minusDays(27L - i),
                painLevel = p,
                painDuration = when {
                    p <= 3 -> PainDuration.A_FEW_MOMENTS
                    p <= 5 -> PainDuration.A_FEW_HOURS
                    p <= 7 -> PainDuration.MOST_OF_THE_DAY
                    else -> PainDuration.ALL_DAY
                },
                sleepHours = (7.5 - (p - 5) * 0.3).coerceIn(4.0, 9.0),
                mood = when { p >= 7 -> Mood.BAD; p >= 4 -> Mood.OKAY; else -> Mood.GOOD },
                nutrition = Nutrition.OKAY,
                tookAllMedications = true,
                exerciseIntensity = Intensity.GENTLE
            )
        }

        val daily = (0 until 7).map { i ->
            DailyExtra(
                date = today.minusDays(6L - i),
                fatigue = FATIGUE[i],
                interference = INTERFERENCE[i],
                recovery = RECOVERY[i]
            )
        }

        val outcomes = mutableMapOf<String, SessionOutcome>()
        val sessions = SESSIONS.mapIndexed { i, s ->
            val id = "sample-session-$i"
            outcomes[id] = SessionOutcome(s.cutShort, s.reason, s.symptoms)
            ExerciseSession(
                id = id,
                date = LocalDate.of(2026, s.month, s.day).plusDays(shift),
                scheduledTime = if (i % 2 == 0) LocalTime.of(9, 0) else LocalTime.of(18, 0),
                intensity = if (s.during == PainFeeling.A_LOT) Intensity.GENTLE else Intensity.FULL,
                items = emptyList(),
                status = s.status,
                painDuring = s.during,
                painAfter = s.after
            )
        }

        // Sundays: 21, 14, 7 and 0 days before the anchor.
        val peg = PEG.mapIndexed { i, (p, e, g) -> PegEntry(today.minusDays(21L - 7L * i), p, e, g) }

        return ReportData.build(
            today = today, now = now, patient = patient,
            checkIns = checkIns, sessions = sessions,
            daily = daily, peg = peg, outcomes = outcomes,
            sampleNotes = listOf(WARNING)
        )
    }
}
