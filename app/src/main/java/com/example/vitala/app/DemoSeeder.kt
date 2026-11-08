package com.example.vitala.app

import com.example.vitala.Recognition.AdjustmentRule
import com.example.vitala.Recognition.Exercise
import com.example.vitala.Recognition.SchedulePhase
import com.example.vitala.Recognition.StopLevel
import com.example.vitala.Recognition.StopRule
import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.ExerciseItem
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.PainFeeling
import com.example.vitala.data.Recovery
import com.example.vitala.data.RecoveryStatus
import com.example.vitala.data.RecoveryTime
import com.example.vitala.data.SessionStatus
import com.example.vitala.data.SymptomResponse
import com.example.vitala.pacing.adaptive.CeilingPeriod
import com.example.vitala.pacing.adaptive.DoseDimension
import com.example.vitala.pacing.adaptive.ExerciseDay
import com.example.vitala.pacing.adaptive.ExerciseDose
import com.example.vitala.pacing.adaptive.ExercisePrescription
import com.example.vitala.pacing.adaptive.ExerciseState
import com.example.vitala.pacing.adaptive.ProgressionPolicy
import com.example.vitala.pacing.adaptive.ReductionPolicy
import com.example.vitala.pacing.adaptive.ReductionSource
import com.example.vitala.pacing.adaptive.SessionRecord
import com.example.vitala.pacing.adaptive.SignalRule
import com.example.vitala.pacing.integration.CompiledExercise
import com.example.vitala.pacing.integration.PreparedProgram
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.vitala.pacing.adaptive.SessionStatus as EngineStatus
import com.example.vitala.Recognition.ExercisePrescription as Scanned

/**
 * Builds the demo user from the brief's Appendix A (Maria Lutke, graded hand tendon gliding) so the app
 * can be shown without typing anything. Dates are RELATIVE: the plan starts 6 days ago, so "today" is
 * always day 7 of 21, with six days of history.
 *
 * It fills the in-memory data sources (check-ins, sessions, app records and the engine's own program state) so
 * Home, Insights, Plan and the engine behave as for a real user.
 */
class DemoSeeder(private val backend: LocalBackend) {
    private val store = backend.store
    private val health = backend.health

    companion object {
        const val UID = "demo-maria"
        const val PROGRAM_ID = "demo-maria-v1"
        private val pretty = DateTimeFormatter.ofPattern("MMM d, yyyy")
    }

    // pre-session pain, average pain, fatigue, daily life for days 1..6 (Appendix A.8; the gaps are filled in)
    private val prePain = listOf(3, 3, 5, 4, 3, 3)
    private val avgPain = listOf(3, 3, 5, 4, 3, 3)
    private val fatigue = listOf(3, 3, 4, 3, 3, 2)
    private val life = listOf(2, 3, 4, 3, 2, 2)
    // doses actually assigned and done: day 3 was reduced to 1 minute; day 6 the plan allowed 2 again
    private val doses = listOf(2.0, 2.0, 1.0, 1.0, 1.0, 2.0)

    suspend fun seed(uid: String, today: LocalDate) {
        val start = today.minusDays(6)
        val exId = "$PROGRAM_ID-0"
        val source = source(start)
        val plan = plan(exId, start, source)
        val prepared = PreparedProgram(PROGRAM_ID, source, listOf(CompiledExercise(0, plan, questions(source))))
        val allFalse = (questions(source).keys).associateWith { false }
        val state = ExerciseState(exId, ExerciseDose(DoseDimension.MINUTES, 2.0), doseEffectiveFrom = start.plusDays(5), lastIncreaseDate = start.plusDays(5))
        val reports = mutableListOf<ExerciseDay>()
        val assigned = mutableMapOf<LocalDate, ExerciseDose>()

        for (i in 0 until 6) {
            val date = start.plusDays(i.toLong())
            val dose = ExerciseDose(DoseDimension.MINUTES, doses[i])
            assigned[date] = dose
            reports += ExerciseDay(
                exerciseId = exId, date = date, preExercisePain = prePain[i],
                session = SessionRecord(EngineStatus.COMPLETED, dose, dose), signals = allFalse
            )

            // check-in + session in the places Insights and the PDF read
            health.saveCheckIn(uid, DailyCheckIn(
                date = date, painLevel = avgPain[i], fatigue = fatigue[i], interference = life[i],
                recovery = if (i == 2) Recovery(RecoveryStatus.RETURNED, RecoveryTime.NEXT_DAY) else null
            ))
            val session = ExerciseSession(
                id = date.toString(), patientId = uid, date = date.toString(), scheduledTime = "10:30",
                items = listOf(ExerciseItem(exId, "Gentle tendon glides", durationMinutes = doses[i].toInt()))
            )
            session.status = SessionStatus.DONE
            session.symptomResponse = SymptomResponse.WITHIN_RANGE
            session.painBefore = if (prePain[i] == 0) PainFeeling.NONE else if (prePain[i] < 5) PainFeeling.SOME else PainFeeling.A_LOT
            health.saveSession(uid, session)

            store.put(uid, "days", date.toString(), DayRec(
                date = date.toString(), pain = prePain[i], applied = true, logged = true, levelChosen = Level.PLANNED.name,
                suggestedTime = "10:30", symptomResponse = SymptomResponse.WITHIN_RANGE.name
            ))
            store.put(uid, "recommendations", date.toString(), RecommendationRec(
                date = date.toString(), level = Level.PLANNED.name, programIds = listOf(PROGRAM_ID),
                reason = when (i) {
                    0 -> "This is the amount your plan starts with."
                    2 -> "Pain was 2 points higher than the day before, so your plan reduced the time by 1 minute."
                    5 -> "Your recent sessions went steadily, and your plan allows 2 minutes today."
                    else -> "Your pain is the same or lower, so your time stays the same."
                }
            ))
        }
        backend.adaptive.seed(prepared, state, reports, assigned, lastAssigned = start.plusDays(5))

        val cond = ConditionRec(name = "Right hand and wrist sensitivity", diagnosis = "Treated carpal tunnel syndrome")
        store.put(uid, "conditions", cond.id, cond)
        store.put(uid, "plans", PROGRAM_ID, PlanVersionRec(
            id = PROGRAM_ID, conditionId = cond.id, version = 1, doctor = source.prescriber, planDate = source.dateApproved,
            startDate = source.startDate, savedOn = start.minusDays(1).toString(), source = "photo",
            current = true, items = PlanReview.build(source, prepared)
        ))
        store.saveProfile(uid, Profile(firstName = "Maria", lastName = "Lutke", medicalProvider = "Treating physiotherapist", onboardingStep = 7))
        store.saveSettings(uid, Settings(
            textSize = TextSizeChoice.LARGE.name, usualExerciseTime = "10:30", leadMinutes = 15,
            checkInTime = "19:00", weeklyDay = 7, weeklyTime = "10:00", quietStart = "21:00", quietEnd = "07:00",
            exerciseTimes = listOf(ExerciseTime("Gentle tendon glides", exId, "10:30", 15))
        ))
    }

    private fun questions(source: Scanned): Map<String, String> = linkedMapOf<String, String>().also { q ->
        source.stopRules.forEachIndexed { i, r -> q["safety_$i"] = r.trigger }
        q["blocker_0"] = "Did your pain stay higher than usual after your last session?"
    }

    private fun source(start: LocalDate): Scanned = Scanned(
        programName = "Graded hand tendon gliding",
        bodyArea = "right hand/wrist",
        clinicalContext = "Persistent right hand and wrist sensitivity after previously treated carpal tunnel syndrome; no current acute injury.",
        treatmentGoals = "Comfortable movement, confidence using the right hand, and gradual return to everyday activity.",
        startDate = start.format(pretty),
        reviewPlan = "After 3 weeks (about ${start.plusDays(21).format(pretty)}), or sooner if the concerns on the plan occur.",
        prescriber = "Treating physiotherapist, (111) 111-1111",
        clinicContact = "(222) 222-2222",
        dateApproved = start.minusDays(1).format(pretty),
        exercises = listOf(Exercise(
            name = "Gentle tendon glides",
            setup = "Sit comfortably, forearm supported, shoulder relaxed. Keep the wrist comfortably straight. Move slowly and breathe normally.",
            steps = listOf(
                "Hand upright, fingers and thumb pointing up",
                "Hook: gently bend the middle and end joints of the fingers",
                "Loose fist: gently bend the big knuckles",
                "Straight fist: open the end joints a little",
                "Return to the start position"
            ),
            pace = "One cycle takes about 10 to 15 seconds. Rest 20 to 30 seconds between cycles. Slow and comfortable.",
            sets = "", reps = "", holdOrDuration = "Up to 2 minutes, with short rests",
            frequency = "Once a day", intensityOrLoad = "Slow, comfortable range",
            precautions = listOf("Do not squeeze, force the fingertips into the palm, or push the fingers with the other hand.")
        )),
        schedule = listOf(
            SchedulePhase("Days 1 to 7", "2 minutes", "once daily", "ceiling, not a deadline"),
            SchedulePhase("Days 8 to 14", "Up to 3 minutes", "once daily", "ceiling, not a deadline"),
            SchedulePhase("Days 15 to 21", "Up to 4 minutes", "once daily", "ceiling, not a deadline")
        ),
        limits = listOf("Never exceed 4 minutes in one session.", "The minimum session for an automatic reduction is 1 minute."),
        adjustmentRules = listOf(
            AdjustmentRule("A", "pain before today's session is at least 2 points higher than yesterday's", "reduce by 1 minute, never below 1 minute"),
            AdjustmentRule("B", "pain is unchanged or lower", "keep the last assigned session time"),
            AdjustmentRule("C", "2 consecutive sessions at the current dose with no rise in next-morning pain", "increase by 1 minute, at most once in 7 days"),
            AdjustmentRule("D", "yesterday's pain or time is missing", "keep the last assigned dose; do not read missing as zero")
        ),
        trackingInstructions = listOf("Before each session: familiar hand or wrist pain 0 to 10.", "After each session: the time actually completed."),
        stopRules = listOf(
            StopRule(StopLevel.URGENT_CARE, "sudden significant weakness, a cold or pale hand, or severe rapidly worsening symptoms"),
            StopRule(StopLevel.STOP_AND_SEEK_ASSESSMENT, "new or worsening numbness, persistent tingling, loss of hand strength, dropping objects, marked swelling, or a change in hand colour or temperature"),
            StopRule(StopLevel.CONTACT_TEAM, "a flare continues into the following day, pain keeps rising despite reductions, or you are repeatedly unable to complete the minimum session"),
            StopRule(StopLevel.PAUSE, "discomfort keeps building, feels substantially different from usual symptoms, or does not settle with a short rest")
        ),
        generalPrecautions = listOf("Some familiar discomfort may occur. A temporary increase in familiar pain does not by itself mean the tissue has been damaged."),
        otherNotes = emptyList(),
        unreadable = emptyList()
    )

    private fun plan(exId: String, start: LocalDate, source: Scanned) = ExercisePrescription(
        exerciseId = exId, activity = "Gentle tendon glides", bodyArea = source.bodyArea, programStart = start,
        startingDose = ExerciseDose(DoseDimension.MINUTES, 2.0),
        ceilings = listOf(CeilingPeriod(1, 7, 2.0), CeilingPeriod(8, 14, 3.0), CeilingPeriod(15, 21, 4.0)),
        reduction = ReductionPolicy(2, 1.0, 1.0, ReductionSource.PREVIOUS_ACTUAL_DOSE),
        progression = ProgressionPolicy(1.0, 2, 7, 0),
        frequencyInstructions = "Once a day",
        fixedInstructions = (listOf(source.exercises[0].setup) + source.exercises[0].steps + source.exercises[0].pace + source.exercises[0].precautions).joinToString("\n"),
        urgentRules = rule(source, StopLevel.URGENT_CARE), stopRules = rule(source, StopLevel.STOP_AND_SEEK_ASSESSMENT),
        pauseRules = rule(source, StopLevel.PAUSE), reviewRules = rule(source, StopLevel.CONTACT_TEAM),
        progressionBlockerIds = setOf("blocker_0"),
        incompleteInstructions = "Do not make up a missed session. Record the time you actually did.",
        minimumDoseInstructions = "1 minute is the smallest automatic session. Contact the physiotherapy team if the increase persists.",
        missingDataInstructions = "Keep the last assigned dose within the ceiling. After several missed days, contact the physiotherapist.",
        scheduledReviewInstructions = "Your 3-week review is due. Contact your physiotherapist before going further."
    )

    private fun rule(source: Scanned, level: StopLevel) = source.stopRules.mapIndexedNotNull { i, r ->
        if (r.level == level) SignalRule("safety_$i", "${level.title}: ${r.trigger}. ${source.clinicContact}") else null
    }
}
