package com.example.vitala.pacing

import java.time.LocalDate

object PacingDemo {

    fun run(
        recoveryToday: Recovery = Recovery.NEXT_DAY
    ): Recommendation {
        val today = LocalDate.now()

        // 1. Simulate the information provided by the doctor.
        val doctorPrescription = DemoPrescription.walking(
            effectiveFrom = today.minusDays(7)
        )

        // 2. Simulate five completed exercise sessions.
        val sessionReports = (6 downTo 2).map { daysAgo ->
            SessionReport(
                id = "session-$daysAgo",
                exerciseId = doctorPrescription.exerciseId,
                date = today.minusDays(daysAgo.toLong()),

                // The dose prescribed when this session took place.
                prescribed = doctorPrescription.currentDose,

                // The dose the patient reports actually completing.
                actual = doctorPrescription.currentDose,

                confirmedComplete = true,
                symptoms = Symptoms.FAMILIAR_WITHIN_RANGE
            )
        }

        // 3. Simulate seven daily check-ins.
        val dailyReports = (6 downTo 0).map { daysAgo ->
            DailyReport(
                date = today.minusDays(daysAgo.toLong()),

                pain = 4,
                fatigue = 3,
                interference = 2,

                // Usual reference values supplied for this example.
                referencePain = 4,
                referenceFatigue = 3,
                referenceInterference = 2,

                recovery = if (daysAgo == 0) {
                    recoveryToday
                } else {
                    Recovery.NEXT_DAY
                },

                contextChanged = false,

                triggerAnswers = mapOf(
                    "urgent_trigger" to false,
                    "review_trigger" to false,
                    "recovery_window_exceeded" to false
                )
            )
        }

        // 4. Simulate the weekly PEG questionnaire.
        val weeklyPegReports = listOf(
            PegReport(
                date = today,
                averagePain = 4,
                enjoymentInterference = 2,
                activityInterference = 2
            )
        )

        // 5. Combine the patient's reports into a history.
        val history = PatientHistory(
            sessions = sessionReports,
            days = dailyReports,
            peg = weeklyPegReports
        )

        // 6. Interpret the reports using the doctor's rules.
        val recommendation = PacingEngine().recommend(
            plan = doctorPrescription,
            history = history,
            today = today
        )

        // 7. Display the result.
        println("Action: ${recommendation.action}")
        println("Proposed dose: ${recommendation.nextDose}")
        println("Reason: ${recommendation.reason}")
        println("Instructions: ${recommendation.instructions}")
        println("Matched rule: ${recommendation.matchedRule}")

        return recommendation
    }
}