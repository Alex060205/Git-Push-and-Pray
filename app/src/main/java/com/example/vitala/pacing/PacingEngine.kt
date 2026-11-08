package com.example.vitala.pacing

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class PacingEngine {

    fun recommend(
        plan: DoctorPrescription,
        history: PatientHistory,
        today: LocalDate
    ): Recommendation {

        fun result(
            action: Action,
            rule: String,
            reason: String,
            dose: Dose? = null,
            instructions: String = "",
            evidence: Map<String, String> = emptyMap()
        ) = Recommendation(
            action = action,
            nextDose = dose,
            reason = reason,
            instructions = instructions,
            matchedRule = rule,
            evidence = evidence
        )

        fun missing(reason: String) = result(
            Action.NEEDS_INFORMATION,
            "MISSING_OR_UNCERTAIN_DATA",
            reason
        )

        val todayReports = history.days.filter { it.date == today }

        if (todayReports.size != 1) {
            return missing("Provide exactly one current daily check-in.")
        }

        val daily = todayReports.single()

        val sessions = history.sessions
            .filter {
                it.exerciseId == plan.exerciseId &&
                        !it.date.isBefore(plan.effectiveFrom) &&
                        !it.date.isAfter(today)
            }
            .sortedBy { it.date }

        val latestSession = sessions.lastOrNull()

        val peg = history.peg
            .filter {
                !it.date.isAfter(today) &&
                        !it.date.isBefore(today.minusDays(6))
            }
            .maxByOrNull { it.date }

        val currentFacts = facts(daily, latestSession, peg)

        // 1. Urgent rule.
        when (plan.urgentWhen.evaluate(currentFacts)) {
            Match.YES -> return result(
                Action.URGENT,
                "URGENT_TRIGGER",
                "A prescribed urgent-care trigger was reported.",
                plan.urgentAction.dose,
                plan.urgentAction.instructions
            )

            Match.UNKNOWN -> return missing(
                "Complete the questions needed to check urgent-care triggers."
            )

            Match.NO -> Unit
        }

        // 2. Review rule.
        when (plan.reviewWhen.evaluate(currentFacts)) {
            Match.YES -> return result(
                Action.REVIEW,
                "REVIEW_TRIGGER",
                "Your report meets the clinician's review criteria.",
                plan.reviewAction.dose,
                plan.reviewAction.instructions
            )

            Match.UNKNOWN -> return missing(
                "Complete the information needed to check review criteria."
            )

            Match.NO -> Unit
        }

        // 3. Verify prescription configuration.
        val progression = plan.progression
        val currentValue =
            plan.currentDose.values[progression.dimension]

        val configurationComplete =
            currentValue != null &&
                    currentValue <= progression.maximum &&
                    plan.activity.isNotBlank() &&
                    plan.technique.isNotBlank() &&
                    plan.fallback.dose != null &&
                    plan.fallback.instructions.isNotBlank() &&
                    plan.reviewAction.instructions.isNotBlank() &&
                    plan.urgentAction.instructions.isNotBlank() &&
                    IncompleteReason.values().all {
                        val instructions = plan.incompleteInstructions[it]

                        instructions != null &&
                                instructions.duringSession.isNotBlank() &&
                                instructions.nextAttempt.instructions.isNotBlank()
                    }

        if (!configurationComplete ||
            plan.effectiveFrom.isAfter(today)
        ) {
            return result(
                Action.NEEDS_CONFIGURATION,
                "INCOMPLETE_PRESCRIPTION",
                "The clinician must complete or correct this prescription."
            )
        }

        // 4. Rollback rule.
        when (plan.rollbackWhen.evaluate(currentFacts)) {
            Match.YES -> return result(
                Action.DECREASE,
                "ROLLBACK",
                "Your report meets the prescribed fallback criteria.",
                plan.fallback.dose,
                plan.fallback.instructions
            )

            Match.UNKNOWN -> return missing(
                "More information is needed to evaluate fallback criteria."
            )

            Match.NO -> Unit
        }

        // 5. Incomplete session.
        if (latestSession != null &&
            !latestSession.completedAsPrescribed
        ) {
            val instructions = plan.incompleteInstructions[
                latestSession.incompleteReason
            ]!!

            return result(
                Action.INCOMPLETE_SESSION,
                "INCOMPLETE_SESSION",
                "Use the instructions for the reported incomplete-session reason.",
                instructions.nextAttempt.dose,
                instructions.nextAttempt.instructions
            )
        }

        // 6. Enforce minimum time between changes.
        val daysAtDose = ChronoUnit.DAYS.between(
            plan.effectiveFrom,
            today
        )

        if (daysAtDose < progression.minimumDaysBetweenChanges) {
            return result(
                Action.MAINTAIN,
                "MINIMUM_INTERVAL",
                "The minimum interval before progression has not elapsed.",
                plan.currentDose,
                plan.technique
            )
        }

        // 7. Require the full observation window at this dose.
        val start = today.minusDays(
            progression.observationDays.toLong() - 1
        )

        if (start.isBefore(plan.effectiveFrom)) {
            return result(
                Action.MAINTAIN,
                "OBSERVATION_PERIOD",
                "More time at the current dose is required.",
                plan.currentDose,
                plan.technique
            )
        }

        val observedDays = history.days.filter {
            !it.date.isBefore(start) && !it.date.isAfter(today)
        }

        val expectedDates = (0 until progression.observationDays)
            .map { start.plusDays(it.toLong()) }
            .toSet()

        if (observedDays.size != expectedDates.size ||
            observedDays.map { it.date }.toSet() != expectedDates
        ) {
            return missing(
                "Complete daily observations, including rest days."
            )
        }

        // Missing recovery and explicit uncertainty block progression.
        if (observedDays.any {
                it.recovery == null || it.recovery == Recovery.UNSURE
            }
        ) {
            return missing(
                "Recovery information is missing or uncertain."
            )
        }

        // Weekly PEG is required before considering progression.
        if (peg == null) {
            return missing("Complete the weekly PEG questionnaire.")
        }

        // 8. Evaluate daily tolerance across the observation window.
        val dailyMatches = observedDays.map {
            plan.acceptableDailyResponse.evaluate(
                facts(it, null, null)
            )
        }

        if (Match.UNKNOWN in dailyMatches) {
            return missing(
                "Information required by the daily tolerance criteria is missing."
            )
        }

        if (Match.NO in dailyMatches) {
            return result(
                Action.MAINTAIN,
                "DAILY_TOLERANCE_NOT_MET",
                "The observation window does not meet progression criteria.",
                plan.currentDose,
                plan.technique
            )
        }

        // 9. Evaluate sessions at the current prescription.
        val windowSessions = sessions.filter {
            !it.date.isBefore(start)
        }

        val sessionMatches = windowSessions.map {
            plan.acceptableSessionResponse.evaluate(
                facts(null, it, null)
            )
        }

        if (Match.UNKNOWN in sessionMatches) {
            return missing("Required session information is missing.")
        }

        val qualifyingSessions = windowSessions.count {
            it.completedAsPrescribed &&
                    it.prescribed == plan.currentDose &&
                    plan.acceptableSessionResponse.evaluate(
                        facts(null, it, null)
                    ) == Match.YES
        }

        if (Match.NO in sessionMatches ||
            qualifyingSessions < progression.requiredCompletedSessions
        ) {
            return result(
                Action.MAINTAIN,
                "SESSION_EVIDENCE_NOT_MET",
                "More qualifying sessions are needed before progression.",
                plan.currentDose,
                plan.technique,
                mapOf(
                    "qualifyingSessions" to qualifyingSessions.toString(),
                    "requiredSessions" to
                            progression.requiredCompletedSessions.toString()
                )
            )
        }

        // 10. Apply any clinician-specified PEG interpretation.
        val weeklyMatch = plan.acceptableWeeklyResponse?.evaluate(
            facts(null, null, peg)
        ) ?: Match.YES

        if (weeklyMatch == Match.UNKNOWN) {
            return missing("Required weekly outcome information is missing.")
        }

        if (weeklyMatch == Match.NO) {
            return result(
                Action.MAINTAIN,
                "WEEKLY_CRITERIA_NOT_MET",
                "Weekly outcomes do not meet the prescribed progression criteria.",
                plan.currentDose,
                plan.technique
            )
        }

        // 11. Change one permitted dimension by exactly the allowed increment.
        val current = requireNotNull(currentValue)
        val next = current + progression.increment

        if (next > progression.maximum) {
            return result(
                Action.MAINTAIN,
                "MAXIMUM_DOSE",
                "The next permitted increment would exceed the maximum.",
                plan.currentDose,
                plan.technique
            )
        }

        return result(
            Action.INCREASE,
            "PROGRESSION",
            "Required evidence and tolerance criteria have been met.",
            plan.currentDose.change(progression.dimension, next),
            plan.technique,
            mapOf(
                "dimension" to progression.dimension.name,
                "previousValue" to current.toString(),
                "nextValue" to next.toString(),
                "qualifyingSessions" to qualifyingSessions.toString()
            )
        )
    }

    private fun facts(
        daily: DailyReport?,
        session: SessionReport?,
        peg: PegReport?
    ): Facts {
        val numbers = mutableMapOf<Metric, Double>()
        val categories = mutableMapOf<Category, String>()

        daily?.let {
            numbers[Metric.PAIN] = it.pain.toDouble()
            numbers[Metric.FATIGUE] = it.fatigue.toDouble()
            numbers[Metric.INTERFERENCE] = it.interference.toDouble()

            it.referencePain?.let { reference ->
                numbers[Metric.PAIN_CHANGE] =
                    (daily.pain - reference).toDouble()
            }

            it.referenceFatigue?.let { reference ->
                numbers[Metric.FATIGUE_CHANGE] =
                    (daily.fatigue - reference).toDouble()
            }

            it.referenceInterference?.let { reference ->
                numbers[Metric.INTERFERENCE_CHANGE] =
                    (daily.interference - reference).toDouble()
            }

            it.recovery?.let { recovery ->
                categories[Category.RECOVERY] = recovery.name
            }

            categories[Category.CONTEXT_CHANGED] =
                it.contextChanged.toString()
        }

        session?.let {
            categories[Category.SYMPTOMS] = it.symptoms.name
        }

        peg?.let {
            numbers[Metric.PEG_PAIN] = it.averagePain.toDouble()
            numbers[Metric.PEG_ENJOYMENT] =
                it.enjoymentInterference.toDouble()
            numbers[Metric.PEG_ACTIVITY] =
                it.activityInterference.toDouble()
        }

        return Facts(
            numbers,
            categories,
            daily?.triggerAnswers ?: emptyMap()
        )
    }
}