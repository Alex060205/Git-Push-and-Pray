package com.example.vitala.pacing.adaptive

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AdaptiveEngine {

    fun recommend(
        prescription: ExercisePrescription,
        state: ExerciseState,
        reports: List<ExerciseDay>,
        today: LocalDate
    ): AdaptiveRecommendation {

        require(state.exerciseId == prescription.exerciseId)
        require(
            state.assignedDose.dimension ==
                    prescription.startingDose.dimension
        )

        fun result(
            action: AdaptiveAction,
            ruleId: String,
            reason: String,
            dose: ExerciseDose? = null,
            instructions: String = ""
        ) = AdaptiveRecommendation(
            exerciseId = prescription.exerciseId,
            evaluatedFor = today,
            action = action,
            nextDose = dose,
            reason = reason,
            instructions = instructions,
            ruleId = ruleId
        )

        if (today.isBefore(prescription.programStart)) {
            return result(
                AdaptiveAction.NOT_STARTED,
                "BEFORE_START_DATE",
                "The program has not started."
            )
        }

        val relevant = reports.filter {
            it.exerciseId == prescription.exerciseId &&
                    !it.date.isAfter(today)
        }

        require(
            relevant.map { it.date }.distinct().size == relevant.size
        ) {
            "Provide at most one report per exercise per day."
        }

        val byDate = relevant.associateBy { it.date }

        val current = byDate[today]
            ?: return result(
                AdaptiveAction.NEEDS_INFORMATION,
                "MISSING_TODAY_REPORT",
                "Today's check-in is missing."
            )

        // Known safety triggers take priority even if other answers are missing.
        val safetyGroups = listOf(
            AdaptiveAction.URGENT to prescription.urgentRules,
            AdaptiveAction.STOP_AND_ASSESS to prescription.stopRules,
            AdaptiveAction.PAUSE to prescription.pauseRules,
            AdaptiveAction.REVIEW to prescription.reviewRules
        )

        for ((action, rules) in safetyGroups) {
            val matched = rules.firstOrNull {
                current.signals[it.id] == true
            }

            if (matched != null) {
                return result(
                    action,
                    matched.id,
                    "A prescribed safety or review condition was reported.",
                    instructions = matched.instructions
                )
            }
        }

        val missingSafetyAnswer = safetyGroups
            .flatMap { it.second }
            .firstOrNull { it.id !in current.signals }

        if (missingSafetyAnswer != null) {
            return result(
                AdaptiveAction.NEEDS_INFORMATION,
                "MISSING_SAFETY_ANSWER",
                "Answer the safety question: ${missingSafetyAnswer.id}."
            )
        }

        val programDay = ChronoUnit.DAYS.between(
            prescription.programStart,
            today
        ).toInt() + 1

        val ceiling = prescription.ceilings.firstOrNull {
            programDay in it.firstDay..it.lastDay
        }?.maximum

        if (ceiling == null) {
            return result(
                AdaptiveAction.REVIEW,
                "SCHEDULED_REVIEW",
                "The prescribed schedule has ended.",
                instructions = prescription.scheduledReviewInstructions
            )
        }

        require(!state.doseEffectiveFrom.isAfter(today)) {
            "Use the state currently in effect, not a future state."
        }

        require(
            state.lastIncreaseDate == null ||
                    !state.lastIncreaseDate.isAfter(today)
        )

        require(
            state.assignedDose.value >= prescription.reduction.minimum
        )

        fun dose(value: Double) = ExerciseDose(
            dimension = prescription.startingDose.dimension,
            value = value
        )

        val assignedToday = dose(
            minOf(state.assignedDose.value, ceiling)
        )

        fun maintain(ruleId: String, reason: String) = result(
            AdaptiveAction.MAINTAIN,
            ruleId,
            reason,
            assignedToday,
            prescription.fixedInstructions
        )

        // First day uses the prescribed starting dose.
        if (programDay == 1) {
            return result(
                AdaptiveAction.START,
                "STARTING_DOSE",
                "Use the prescribed starting dose.",
                dose(minOf(prescription.startingDose.value, ceiling)),
                prescription.fixedInstructions
            )
        }

        val previous = byDate[today.minusDays(1)]
        val previousSession = previous?.session
        val previousPain = previous?.preExercisePain

        // Missing yesterday's information or a missed session:
        // hold the assigned dose rather than interpreting missing as zero.
        if (
            previousPain == null ||
            previousSession == null ||
            previousSession.status == SessionStatus.MISSED ||
            previousSession.actualDose == null
        ) {
            return result(
                AdaptiveAction.MAINTAIN,
                "INCOMPLETE_PREVIOUS_COMPARISON",
                "Yesterday's comparison is incomplete; do not progress.",
                assignedToday,
                prescription.missingDataInstructions
            )
        }

        val todayPain = current.preExercisePain
            ?: return result(
                AdaptiveAction.NEEDS_INFORMATION,
                "MISSING_TODAY_PAIN",
                "Today's pre-exercise pain score is missing."
            )

        require(
            previousSession.assignedDose.dimension ==
                    prescription.startingDose.dimension
        )

        val painChange = todayPain - previousPain

        // Apply reduction only after a reported completed session.
        if (
            previousSession.status == SessionStatus.COMPLETED &&
            painChange >= prescription.reduction.painIncreaseThreshold
        ) {
            val sourceValue = when (prescription.reduction.source) {
                ReductionSource.PREVIOUS_ACTUAL_DOSE ->
                    previousSession.actualDose.value

                ReductionSource.CURRENT_ASSIGNED_DOSE ->
                    state.assignedDose.value
            }

            val reducedValue = maxOf(
                prescription.reduction.minimum,
                minOf(
                    sourceValue - prescription.reduction.decrement,
                    assignedToday.value,
                    ceiling
                )
            )

            if (sourceValue <= prescription.reduction.minimum) {
                return result(
                    AdaptiveAction.MAINTAIN,
                    "MINIMUM_DOSE_REACHED",
                    "Do not automatically reduce below the prescribed minimum.",
                    assignedToday,
                    prescription.minimumDoseInstructions
                )
            }

            // A previous reduction may already be in effect.
            if (reducedValue >= assignedToday.value) {
                return maintain(
                    "REDUCTION_ALREADY_IN_EFFECT",
                    "The assigned dose is already at or below the calculated dose."
                )
            }

            return result(
                AdaptiveAction.DECREASE,
                "PAIN_INCREASE_REDUCTION",
                "Pre-exercise pain increased by $painChange points.",
                dose(reducedValue),
                prescription.fixedInstructions
            )
        }

        if (previousSession.status == SessionStatus.PARTIAL) {
            return result(
                AdaptiveAction.MAINTAIN,
                "PARTIAL_SESSION",
                "Yesterday's session was shortened; do not progress.",
                assignedToday,
                prescription.incompleteInstructions
            )
        }

        // A known blocker holds the dose.
        val hasBlocker = prescription.progressionBlockerIds.any {
            current.signals[it] == true
        }

        if (hasBlocker) {
            return maintain(
                "PROGRESSION_BLOCKED",
                "A prescribed progression blocker is present."
            )
        }

        val missingBlocker = prescription.progressionBlockerIds
            .firstOrNull { it !in current.signals }

        if (missingBlocker != null) {
            return result(
                AdaptiveAction.NEEDS_INFORMATION,
                "MISSING_PROGRESSION_ANSWER",
                "Answer the progression question: $missingBlocker."
            )
        }

        val lastIncrease = state.lastIncreaseDate

        if (
            lastIncrease != null &&
            ChronoUnit.DAYS.between(lastIncrease, today) <
            prescription.progression.minimumDaysBetweenIncreases
        ) {
            return maintain(
                "INCREASE_INTERVAL",
                "The minimum interval between increases has not elapsed."
            )
        }

        // Each qualifying session needs the following day's response.
        val required =
            prescription.progression.requiredConsecutiveSessions

        for (offset in 1..required) {
            val sessionDate = today.minusDays(offset.toLong())
            val responseDate = sessionDate.plusDays(1)

            if (sessionDate.isBefore(state.doseEffectiveFrom)) {
                return maintain(
                    "INSUFFICIENT_CURRENT_DOSE_HISTORY",
                    "More consecutive sessions at the current dose are needed."
                )
            }

            val sessionDay = byDate[sessionDate]
            val responseDay = byDate[responseDate]
            val session = sessionDay?.session

            val beforePain = sessionDay?.preExercisePain
            val afterPain = responseDay?.preExercisePain

            if (
                session == null ||
                beforePain == null ||
                afterPain == null ||
                !session.completedAtAssignedDose ||
                session.assignedDose != state.assignedDose
            ) {
                return maintain(
                    "INSUFFICIENT_CONSECUTIVE_EVIDENCE",
                    "Required consecutive sessions and next-day responses are incomplete."
                )
            }

            if (
                afterPain - beforePain >
                prescription.progression.maximumFollowingDayPainIncrease
            ) {
                return maintain(
                    "FOLLOWING_DAY_RESPONSE",
                    "A following-day pain response does not meet progression criteria."
                )
            }

            // Historical session-day and response-day checks.
            val requiredClearIds = (
                    prescription.urgentRules.map { it.id } +
                            prescription.stopRules.map { it.id } +
                            prescription.pauseRules.map { it.id } +
                            prescription.reviewRules.map { it.id } +
                            prescription.progressionBlockerIds
                    ).toSet()

            val historicalDays = listOfNotNull(sessionDay, responseDay)

            if (historicalDays.any { day ->
                    requiredClearIds.any { day.signals[it] != false }
                }
            ) {
                return maintain(
                    "HISTORICAL_SYMPTOM_EVIDENCE",
                    "Historical symptom checks are missing or do not support progression."
                )
            }
        }

        val nextValue =
            state.assignedDose.value + prescription.progression.increment

        if (nextValue > ceiling) {
            return maintain(
                "WEEKLY_CEILING",
                "The next increment would exceed the current ceiling."
            )
        }

        return result(
            AdaptiveAction.INCREASE,
            "PROGRESSION",
            "Consecutive sessions and following-day responses meet the prescribed criteria.",
            dose(nextValue),
            prescription.fixedInstructions
        )
    }
}