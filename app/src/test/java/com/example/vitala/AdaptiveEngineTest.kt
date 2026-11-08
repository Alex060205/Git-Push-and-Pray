package com.example.vitala.pacing.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AdaptiveEngineTest {

    private val start = LocalDate.of(2026, 11, 9)
    private val engine = AdaptiveEngine()

    private fun clearSignals(
        plan: ExercisePrescription
    ): Map<String, Boolean> {
        val ids = (
                plan.urgentRules.map { it.id } +
                        plan.stopRules.map { it.id } +
                        plan.pauseRules.map { it.id } +
                        plan.reviewRules.map { it.id } +
                        plan.progressionBlockerIds
                ).toSet()

        return ids.associateWith { false }
    }

    private fun report(
        plan: ExercisePrescription,
        date: LocalDate,
        pain: Int,
        dose: ExerciseDose? = null
    ): ExerciseDay {
        return ExerciseDay(
            exerciseId = plan.exerciseId,
            date = date,
            preExercisePain = pain,

            session = dose?.let {
                SessionRecord(
                    status = SessionStatus.COMPLETED,
                    assignedDose = it,
                    actualDose = it
                )
            },

            signals = clearSignals(plan)
        )
    }

    @Test
    fun firstDayUsesStartingDose() {
        val plan = SamplePrescriptions.handExercise(start)

        val state = ExerciseState(
            exerciseId = plan.exerciseId,
            assignedDose = plan.startingDose,
            doseEffectiveFrom = start
        )

        val result = engine.recommend(
            plan,
            state,
            listOf(report(plan, start, 3)),
            start
        )

        assertEquals(AdaptiveAction.START, result.action)
        assertEquals(2.0, result.nextDose!!.value, 0.001)
    }

    @Test
    fun twoPointIncreaseReducesFromPreviousActualDose() {
        val plan = SamplePrescriptions.handExercise(start)
        val today = start.plusDays(1)

        val state = ExerciseState(
            plan.exerciseId,
            plan.startingDose,
            start
        )

        val history = listOf(
            report(plan, start, 3, plan.startingDose),
            report(plan, today, 5)
        )

        val result = engine.recommend(plan, state, history, today)

        assertEquals(AdaptiveAction.DECREASE, result.action)
        assertEquals(1.0, result.nextDose!!.value, 0.001)
    }

    @Test
    fun onePointIncreaseMaintainsDose() {
        val plan = SamplePrescriptions.handExercise(start)
        val today = start.plusDays(1)

        val state = ExerciseState(
            plan.exerciseId,
            plan.startingDose,
            start
        )

        val result = engine.recommend(
            plan,
            state,
            listOf(
                report(plan, start, 3, plan.startingDose),
                report(plan, today, 4)
            ),
            today
        )

        assertEquals(AdaptiveAction.MAINTAIN, result.action)
        assertEquals(2.0, result.nextDose!!.value, 0.001)
    }

    @Test
    fun weekTwoAllowsIncreaseAfterTwoQualifyingSessions() {
        val plan = SamplePrescriptions.handExercise(start)
        val today = start.plusDays(7)

        val state = ExerciseState(
            plan.exerciseId,
            plan.startingDose,
            start
        )

        val history = listOf(
            report(plan, today.minusDays(2), 3, plan.startingDose),
            report(plan, today.minusDays(1), 3, plan.startingDose),
            report(plan, today, 3)
        )

        val result = engine.recommend(plan, state, history, today)

        assertEquals(AdaptiveAction.INCREASE, result.action)
        assertEquals(3.0, result.nextDose!!.value, 0.001)
    }

    @Test
    fun firstWeekCeilingPreventsIncrease() {
        val plan = SamplePrescriptions.handExercise(start)
        val today = start.plusDays(3)

        val state = ExerciseState(
            plan.exerciseId,
            plan.startingDose,
            start
        )

        val history = listOf(
            report(plan, today.minusDays(2), 3, plan.startingDose),
            report(plan, today.minusDays(1), 3, plan.startingDose),
            report(plan, today, 3)
        )

        val result = engine.recommend(plan, state, history, today)

        assertEquals(AdaptiveAction.MAINTAIN, result.action)
        assertEquals("WEEKLY_CEILING", result.ruleId)
    }

    @Test
    fun recentIncreasePreventsAnotherIncrease() {
        val plan = SamplePrescriptions.handExercise(start)
        val today = start.plusDays(14)
        val currentDose = ExerciseDose(DoseDimension.MINUTES, 3.0)

        val state = ExerciseState(
            exerciseId = plan.exerciseId,
            assignedDose = currentDose,
            doseEffectiveFrom = today.minusDays(2),
            lastIncreaseDate = today.minusDays(2)
        )

        val history = listOf(
            report(plan, today.minusDays(2), 3, currentDose),
            report(plan, today.minusDays(1), 3, currentDose),
            report(plan, today, 3)
        )

        val result = engine.recommend(plan, state, history, today)

        assertEquals(AdaptiveAction.MAINTAIN, result.action)
        assertEquals("INCREASE_INTERVAL", result.ruleId)
    }

    @Test
    fun missingYesterdayMaintainsAssignedDose() {
        val plan = SamplePrescriptions.handExercise(start)
        val today = start.plusDays(5)

        val state = ExerciseState(
            plan.exerciseId,
            plan.startingDose,
            start
        )

        val result = engine.recommend(
            plan,
            state,
            listOf(report(plan, today, 3)),
            today
        )

        assertEquals(AdaptiveAction.MAINTAIN, result.action)
        assertEquals(
            "INCOMPLETE_PREVIOUS_COMPARISON",
            result.ruleId
        )
    }

    @Test
    fun urgentSymptomsTakePriorityOverReduction() {
        val plan = SamplePrescriptions.handExercise(start)
        val today = start.plusDays(1)

        val state = ExerciseState(
            plan.exerciseId,
            plan.startingDose,
            start
        )

        val current = report(plan, today, 5).copy(
            signals = clearSignals(plan) +
                    ("urgent_symptoms" to true)
        )

        val result = engine.recommend(
            plan,
            state,
            listOf(
                report(plan, start, 3, plan.startingDose),
                current
            ),
            today
        )

        assertEquals(AdaptiveAction.URGENT, result.action)
        assertEquals(null, result.nextDose)
    }

    @Test
    fun minimumDoseIsNotAutomaticallyReducedFurther() {
        val plan = SamplePrescriptions.handExercise(start)
        val today = start.plusDays(2)
        val minimum = ExerciseDose(DoseDimension.MINUTES, 1.0)

        val state = ExerciseState(
            plan.exerciseId,
            minimum,
            today.minusDays(1)
        )

        val result = engine.recommend(
            plan,
            state,
            listOf(
                report(plan, today.minusDays(1), 3, minimum),
                report(plan, today, 5)
            ),
            today
        )

        assertEquals(AdaptiveAction.MAINTAIN, result.action)
        assertEquals("MINIMUM_DOSE_REACHED", result.ruleId)
        assertEquals(1.0, result.nextDose!!.value, 0.001)
    }

    @Test
    fun sameEngineCanAdjustRepetitionsForAnotherExercise() {
        val plan = SamplePrescriptions.fictionalKneeExercise(start)
        val today = start.plusDays(7)

        val state = ExerciseState(
            plan.exerciseId,
            plan.startingDose,
            start
        )

        val history = listOf(
            report(plan, today.minusDays(3), 3, plan.startingDose),
            report(plan, today.minusDays(2), 3, plan.startingDose),
            report(plan, today.minusDays(1), 3, plan.startingDose),
            report(plan, today, 3)
        )

        val result = engine.recommend(plan, state, history, today)

        assertEquals(AdaptiveAction.INCREASE, result.action)
        assertEquals(
            DoseDimension.REPETITIONS,
            result.nextDose!!.dimension
        )
        assertEquals(7.0, result.nextDose!!.value, 0.001)
    }
}