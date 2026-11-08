package com.example.vitala.pacing

import java.time.LocalDate

object DemoPrescription {

    fun walking(effectiveFrom: LocalDate): DoctorPrescription {

        val current = Dose(
            values = mapOf(
                Dimension.MINUTES to 5.0,
                Dimension.SESSIONS_PER_WEEK to 5.0
            )
        )

        val fallback = current.change(Dimension.MINUTES, 4.0)

        val acceptableRecovery = CategoryCondition(
            Category.RECOVERY,
            setOf(
                Recovery.SAME_DAY.name,
                Recovery.NEXT_DAY.name
            )
        )

        val incomplete = IncompleteReason.values().associateWith {
                reason ->
            IncompleteInstructions(
                duringSession =
                    "Demo instruction for ${reason.name}: " +
                            "follow the supplied incomplete-session plan.",

                nextAttempt = PrescriptionAction(
                    dose = current,
                    instructions =
                        "Demo next-attempt instruction: " +
                                "use the existing schedule; do not add catch-up exercise."
                )
            )
        }

        return DoctorPrescription(
            exerciseId = "walking",
            activity = "Walking",
            technique = "Walk at the prescribed comfortable pace.",
            currentDose = current,
            effectiveFrom = effectiveFrom,

            progression = Progression(
                dimension = Dimension.MINUTES,
                increment = 1.0,
                maximum = 10.0,
                minimumDaysBetweenChanges = 7,
                observationDays = 7,
                requiredCompletedSessions = 5
            ),

            acceptableDailyResponse = AllConditions(
                listOf(
                    acceptableRecovery,

                    NumberCondition(
                        Metric.PAIN_CHANGE,
                        Comparison.LESS_OR_EQUAL,
                        2.0
                    ),

                    NumberCondition(
                        Metric.FATIGUE_CHANGE,
                        Comparison.LESS_OR_EQUAL,
                        2.0
                    ),

                    NumberCondition(
                        Metric.INTERFERENCE_CHANGE,
                        Comparison.LESS_OR_EQUAL,
                        1.0
                    ),

                    CategoryCondition(
                        Category.CONTEXT_CHANGED,
                        setOf("false")
                    )
                )
            ),

            acceptableSessionResponse = CategoryCondition(
                Category.SYMPTOMS,
                setOf(Symptoms.FAMILIAR_WITHIN_RANGE.name)
            ),

            // PEG completion is required.
            // No invented numerical PEG cutoff.
            acceptableWeeklyResponse = null,

            rollbackWhen = AnyCondition(
                listOf(
                    CategoryCondition(
                        Category.RECOVERY,
                        setOf(Recovery.LATER.name)
                    ),

                    NumberCondition(
                        Metric.FATIGUE_CHANGE,
                        Comparison.GREATER_THAN,
                        2.0
                    ),

                    NumberCondition(
                        Metric.INTERFERENCE_CHANGE,
                        Comparison.GREATER_THAN,
                        1.0
                    ),

                    AnswerCondition("recovery_window_exceeded")
                )
            ),

            reviewWhen = AnyCondition(
                listOf(
                    AnswerCondition("review_trigger"),

                    CategoryCondition(
                        Category.SYMPTOMS,
                        setOf(Symptoms.NEW_OR_DIFFERENT.name)
                    )
                )
            ),

            urgentWhen = AnswerCondition("urgent_trigger"),

            fallback = PrescriptionAction(
                dose = fallback,
                instructions =
                    "Use the supplied fallback schedule and continue check-ins."
            ),

            reviewAction = PrescriptionAction(
                dose = null,
                instructions =
                    "Contact the designated clinician. " +
                            "Follow their supplied instructions while awaiting review."
            ),

            urgentAction = PrescriptionAction(
                dose = null,
                instructions =
                    "Follow the clinician's supplied urgent-care instructions."
            ),

            incompleteInstructions = incomplete
        )
    }
}