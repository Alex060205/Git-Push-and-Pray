package com.example.vitala.pacing.adaptive

import java.time.LocalDate

object SamplePrescriptions {

    fun handExercise(
        start: LocalDate
    ): ExercisePrescription {
        return ExercisePrescription(
            exerciseId = "right-hand-tendon-glides",
            activity = "Gentle tendon glides",
            bodyArea = "Right hand and wrist",
            programStart = start,

            startingDose = ExerciseDose(
                DoseDimension.MINUTES,
                2.0
            ),

            ceilings = listOf(
                CeilingPeriod(1, 7, 2.0),
                CeilingPeriod(8, 14, 3.0),
                CeilingPeriod(15, 21, 4.0)
            ),

            reduction = ReductionPolicy(
                painIncreaseThreshold = 2,
                decrement = 1.0,
                minimum = 1.0,
                source = ReductionSource.PREVIOUS_ACTUAL_DOSE
            ),

            progression = ProgressionPolicy(
                increment = 1.0,
                requiredConsecutiveSessions = 2,
                minimumDaysBetweenIncreases = 7,
                maximumFollowingDayPainIncrease = 0
            ),

            frequencyInstructions =
                "One session daily. Do not add sessions or make up missed sessions.",

            fixedInstructions =
                "Use the affected right hand. Support the forearm and keep " +
                        "the wrist comfortably straight. Move slowly without squeezing " +
                        "or forcing the range. One cycle takes approximately 10–15 seconds. " +
                        "Rest approximately 20–30 seconds between cycles. " +
                        "The assigned time includes short rests. " +
                        "Do not increase speed, grip force or movement range.",

            urgentRules = listOf(
                SignalRule(
                    id = "urgent_symptoms",
                    instructions =
                        "Stop exercise and seek urgent care for sudden significant " +
                                "weakness, a cold/pale hand, or severe rapidly worsening symptoms."
                )
            ),

            stopRules = listOf(
                SignalRule(
                    id = "assessment_symptoms",
                    instructions =
                        "Stop exercise and seek assessment for new or worsening " +
                                "numbness, persistent tingling, loss of hand strength, " +
                                "dropping objects, marked swelling, or changes in hand " +
                                "color or temperature."
                )
            ),

            pauseRules = listOf(
                SignalRule(
                    id = "pause_symptoms",
                    instructions =
                        "Pause if discomfort continues to build, feels substantially " +
                                "different, or does not settle with a short rest. " +
                                "Record the time actually completed."
                )
            ),

            reviewRules = listOf(
                SignalRule(
                    id = "review_needed",
                    instructions =
                        "Contact the physiotherapy team before further progression " +
                                "if a flare continues into the following day, pain repeatedly " +
                                "increases despite reductions, or the minimum session " +
                                "repeatedly cannot be completed."
                ),

                SignalRule(
                    id = "restart_advice_needed",
                    instructions =
                        "Contact the physiotherapist if several days were missed " +
                                "or you are unsure how to restart. Do not make up sessions."
                )
            ),

            progressionBlockerIds = setOf(
                "new_or_worsening_neurological_symptoms",
                "unusual_or_prolonged_flare"
            ),

            incompleteInstructions =
                "Record the actual time. Do not add catch-up exercise. " +
                        "Follow the pause, assessment and review instructions when applicable.",

            minimumDoseInstructions =
                "Do not automatically reduce below 1 minute. Contact the " +
                        "physiotherapy team if the pain increase persists. " +
                        "Stop instructions take priority.",

            missingDataInstructions =
                "Keep the last assigned dose within the weekly ceiling. " +
                        "Do not progress from an incomplete comparison. " +
                        "Do not make up missed exercise.",

            scheduledReviewInstructions =
                "The planned 21-day schedule has ended. Arrange the prescribed " +
                        "review before further automatic adjustment."
        )
    }

    /**
     * Fictional example only.
     * This is not a clinical prescription for knee exercise.
     */
    fun fictionalKneeExercise(
        start: LocalDate
    ): ExercisePrescription {
        return ExercisePrescription(
            exerciseId = "demo-knee-movement",
            activity = "Demo knee movement",
            bodyArea = "Left knee",
            programStart = start,

            startingDose = ExerciseDose(
                DoseDimension.REPETITIONS,
                5.0
            ),

            ceilings = listOf(
                CeilingPeriod(1, 7, 5.0),
                CeilingPeriod(8, 14, 7.0),
                CeilingPeriod(15, 21, 9.0)
            ),

            reduction = ReductionPolicy(
                painIncreaseThreshold = 2,
                decrement = 1.0,
                minimum = 3.0,
                source = ReductionSource.CURRENT_ASSIGNED_DOSE
            ),

            progression = ProgressionPolicy(
                increment = 2.0,
                requiredConsecutiveSessions = 3,
                minimumDaysBetweenIncreases = 7
            ),

            frequencyInstructions = "Fictional demo: one session daily.",
            fixedInstructions = "Fictional clinician-supplied technique.",

            urgentRules = listOf(
                SignalRule(
                    "urgent_symptoms",
                    "Follow the demo urgent-care instructions."
                )
            ),

            stopRules = listOf(
                SignalRule(
                    "assessment_symptoms",
                    "Follow the demo assessment instructions."
                )
            ),

            pauseRules = listOf(
                SignalRule(
                    "pause_symptoms",
                    "Follow the demo pause instructions."
                )
            ),

            reviewRules = listOf(
                SignalRule(
                    "review_needed",
                    "Follow the demo review instructions."
                )
            ),

            progressionBlockerIds = setOf(
                "unusual_or_prolonged_flare"
            ),

            incompleteInstructions = "Demo incomplete-session instructions.",
            minimumDoseInstructions = "Demo minimum-dose instructions.",
            missingDataInstructions = "Demo missing-data instructions.",
            scheduledReviewInstructions = "Demo scheduled-review instructions."
        )
    }
}