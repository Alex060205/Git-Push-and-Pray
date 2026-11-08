package com.example.vitala.pacing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PacingEngineTest {

    private val today = LocalDate.of(2026, 11, 8)

    private val plan = DemoPrescription.walking(
        effectiveFrom = today.minusDays(7)
    )

    private val engine = PacingEngine()

    private fun createStableHistory(): PatientHistory {

        // Seven daily check-ins, including days without exercise.
        val dailyReports = (6 downTo 0).map { daysAgo ->
            DailyReport(
                date = today.minusDays(daysAgo.toLong()),
                pain = 4,
                fatigue = 3,
                interference = 2,
                referencePain = 4,
                referenceFatigue = 3,
                referenceInterference = 2,
                recovery = Recovery.NEXT_DAY,
                contextChanged = false,
                triggerAnswers = mapOf(
                    "urgent_trigger" to false,
                    "review_trigger" to false,
                    "recovery_window_exceeded" to false
                )
            )
        }

        // Five sessions completed at the prescribed dose.
        val sessions = (6 downTo 2).map { daysAgo ->
            SessionReport(
                id = "session-$daysAgo",
                exerciseId = plan.exerciseId,
                date = today.minusDays(daysAgo.toLong()),
                prescribed = plan.currentDose,
                actual = plan.currentDose,
                confirmedComplete = true,
                symptoms = Symptoms.FAMILIAR_WITHIN_RANGE
            )
        }

        val weeklyQuestionnaire = PegReport(
            date = today,
            averagePain = 4,
            enjoymentInterference = 2,
            activityInterference = 2
        )

        return PatientHistory(
            sessions = sessions,
            days = dailyReports,
            peg = listOf(weeklyQuestionnaire)
        )
    }

    @Test
    fun stableHistoryIncreasesFromFiveToSixMinutes() {
        val result = engine.recommend(
            plan,
            createStableHistory(),
            today
        )

        assertEquals(Action.INCREASE, result.action)
        assertEquals(
            6.0,
            result.nextDose!!.values.getValue(Dimension.MINUTES),
            0.001
        )
    }

    @Test
    fun insufficientSessionsMaintainCurrentDose() {
        val original = createStableHistory()

        val history = original.copy(
            sessions = original.sessions.take(2)
        )

        val result = engine.recommend(plan, history, today)

        assertEquals(Action.MAINTAIN, result.action)
    }

    @Test
    fun delayedRecoveryAppliesFallbackDose() {
        val original = createStableHistory()

        val history = original.copy(
            days = original.days.map { report ->
                if (report.date == today) {
                    report.copy(recovery = Recovery.LATER)
                } else {
                    report
                }
            }
        )

        val result = engine.recommend(plan, history, today)

        assertEquals(Action.DECREASE, result.action)
        assertEquals(
            4.0,
            result.nextDose!!.values.getValue(Dimension.MINUTES),
            0.001
        )
    }

    @Test
    fun newSymptomsRequireReview() {
        val original = createStableHistory()

        val history = original.copy(
            sessions = original.sessions.mapIndexed { index, session ->
                if (index == original.sessions.lastIndex) {
                    session.copy(
                        symptoms = Symptoms.NEW_OR_DIFFERENT
                    )
                } else {
                    session
                }
            }
        )

        val result = engine.recommend(plan, history, today)

        assertEquals(Action.REVIEW, result.action)
    }

    @Test
    fun missingPegQuestionnaireRequestsInformation() {
        val history = createStableHistory().copy(
            peg = emptyList()
        )

        val result = engine.recommend(plan, history, today)

        assertEquals(Action.NEEDS_INFORMATION, result.action)
    }

    @Test
    fun urgentTriggerTakesPriorityOverDoseReduction() {
        val original = createStableHistory()

        val history = original.copy(
            days = original.days.map { report ->
                if (report.date == today) {
                    report.copy(
                        recovery = Recovery.LATER,
                        triggerAnswers = report.triggerAnswers +
                                ("urgent_trigger" to true)
                    )
                } else {
                    report
                }
            }
        )

        val result = engine.recommend(plan, history, today)

        assertEquals(Action.URGENT, result.action)
    }

    @Test
    fun completeDemoRecommendsIncrease() {
        val recommendation = PacingDemo.run()

        assertEquals(Action.INCREASE, recommendation.action)
        assertEquals(
            6.0,
            recommendation.nextDose!!.values.getValue(Dimension.MINUTES),
            0.001
        )
    }

    @Test
    fun demoWithNormalRecoveryRecommendsIncrease() {
        val result = PacingDemo.run(
            recoveryToday = Recovery.NEXT_DAY
        )

        assertEquals(Action.INCREASE, result.action)
        assertEquals(
            6.0,
            result.nextDose!!.values.getValue(Dimension.MINUTES),
            0.001
        )
    }

    @Test
    fun demoWithDelayedRecoveryRecommendsFallback() {
        val result = PacingDemo.run(
            recoveryToday = Recovery.LATER
        )

        assertEquals(Action.DECREASE, result.action)
        assertEquals(
            4.0,
            result.nextDose!!.values.getValue(Dimension.MINUTES),
            0.001
        )
    }

    @Test
    fun demoWithUncertainRecoveryRequestsInformation() {
        val result = PacingDemo.run(
            recoveryToday = Recovery.UNSURE
        )

        assertEquals(Action.NEEDS_INFORMATION, result.action)
    }
}