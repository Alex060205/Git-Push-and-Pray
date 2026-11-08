package com.example.vitala.pacing.adaptive

import java.time.LocalDate

enum class DoseDimension {
    MINUTES,
    REPETITIONS,
    INTENSITY,
    RESISTANCE_KG
}

/**
 * One adjustable dimension per exercise prescription.
 * Other exercise characteristics remain in fixedInstructions.
 */
data class ExerciseDose(
    val dimension: DoseDimension,
    val value: Double
) {
    init {
        require(value.isFinite() && value >= 0.0)

        if (dimension == DoseDimension.REPETITIONS) {
            require(value % 1.0 == 0.0)
        }
    }
}

enum class SessionStatus {
    COMPLETED,
    PARTIAL,
    MISSED
}

data class SessionRecord(
    val status: SessionStatus,
    val assignedDose: ExerciseDose,
    val actualDose: ExerciseDose?
) {
    init {
        require(
            actualDose == null ||
                    actualDose.dimension == assignedDose.dimension
        )

        if (status == SessionStatus.COMPLETED) {
            require(actualDose != null)
        }
    }

    val completedAtAssignedDose: Boolean
        get() = status == SessionStatus.COMPLETED &&
                actualDose == assignedDose
}

/**
 * A report belongs to one exercise and one day.
 *
 * signals:
 * - true: the condition is present
 * - false: explicitly checked and absent
 * - missing key: unknown
 */
data class ExerciseDay(
    val exerciseId: String,
    val date: LocalDate,
    val preExercisePain: Int?,
    val session: SessionRecord? = null,
    val signals: Map<String, Boolean> = emptyMap()
) {
    init {
        require(
            preExercisePain == null ||
                    preExercisePain in 0..10
        )
    }
}

data class CeilingPeriod(
    val firstDay: Int,
    val lastDay: Int,
    val maximum: Double
) {
    init {
        require(firstDay >= 1 && lastDay >= firstDay)
        require(maximum.isFinite() && maximum > 0.0)
    }
}

enum class ReductionSource {
    PREVIOUS_ACTUAL_DOSE,
    CURRENT_ASSIGNED_DOSE
}

data class ReductionPolicy(
    val painIncreaseThreshold: Int,
    val decrement: Double,
    val minimum: Double,
    val source: ReductionSource
) {
    init {
        require(painIncreaseThreshold > 0)
        require(decrement.isFinite() && decrement > 0.0)
        require(minimum.isFinite() && minimum > 0.0)
    }
}

data class ProgressionPolicy(
    val increment: Double,
    val requiredConsecutiveSessions: Int,
    val minimumDaysBetweenIncreases: Int,
    val maximumFollowingDayPainIncrease: Int = 0
) {
    init {
        require(increment.isFinite() && increment > 0.0)
        require(requiredConsecutiveSessions >= 1)
        require(minimumDaysBetweenIncreases >= 1)
    }
}

data class SignalRule(
    val id: String,
    val instructions: String
)

data class ExercisePrescription(
    val exerciseId: String,
    val activity: String,
    val bodyArea: String,

    // Anchors the weekly schedule; reductions do not reset this date.
    val programStart: LocalDate,

    val startingDose: ExerciseDose,
    val ceilings: List<CeilingPeriod>,

    val reduction: ReductionPolicy,
    val progression: ProgressionPolicy,

    val frequencyInstructions: String,
    val fixedInstructions: String,

    val urgentRules: List<SignalRule>,
    val stopRules: List<SignalRule>,
    val pauseRules: List<SignalRule>,
    val reviewRules: List<SignalRule>,

    // These block progression without automatically changing the dose.
    val progressionBlockerIds: Set<String>,

    val incompleteInstructions: String,
    val minimumDoseInstructions: String,
    val missingDataInstructions: String,
    val scheduledReviewInstructions: String
) {
    init {
        require(ceilings.isNotEmpty())

        // Require a continuous schedule beginning on day 1.
        val sorted = ceilings.sortedBy { it.firstDay }
        require(sorted.first().firstDay == 1)

        sorted.zipWithNext().forEach { (previous, next) ->
            require(next.firstDay == previous.lastDay + 1)
        }

        require(startingDose.value >= reduction.minimum)
        require(startingDose.value <= sorted.first().maximum)
        require(ceilings.all { it.maximum >= reduction.minimum })

        if (startingDose.dimension == DoseDimension.REPETITIONS) {
            require(reduction.minimum % 1.0 == 0.0)
            require(reduction.decrement % 1.0 == 0.0)
            require(progression.increment % 1.0 == 0.0)
            require(ceilings.all { it.maximum % 1.0 == 0.0 })
        }
    }
}

/**
 * Persist this separately from the prescription.
 * The application updates it when a change takes effect.
 */
data class ExerciseState(
    val exerciseId: String,
    val assignedDose: ExerciseDose,
    val doseEffectiveFrom: LocalDate,
    val lastIncreaseDate: LocalDate? = null
)

enum class AdaptiveAction {
    START,
    INCREASE,
    MAINTAIN,
    DECREASE,
    PAUSE,
    STOP_AND_ASSESS,
    REVIEW,
    URGENT,
    NEEDS_INFORMATION,
    NOT_STARTED
}

data class AdaptiveRecommendation(
    val exerciseId: String,
    val evaluatedFor: LocalDate,
    val action: AdaptiveAction,
    val nextDose: ExerciseDose?,
    val reason: String,
    val instructions: String,
    val ruleId: String
)