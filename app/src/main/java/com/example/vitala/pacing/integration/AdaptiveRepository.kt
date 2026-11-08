package com.example.vitala.pacing.integration

import com.example.vitala.pacing.adaptive.*
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class AdaptiveRepository(
    private val db: FirebaseFirestore = Firebase.firestore
) : com.example.vitala.app.AdaptiveSource {

    private fun program(
        patientId: String,
        programId: String
    ) = db
        .collection("patients")
        .document(validId(patientId))
        .collection("adaptivePrograms")
        .document(validId(programId))

    private fun exercise(
        patientId: String,
        programId: String,
        exerciseId: String
    ) = program(patientId, programId)
        .collection("exercises")
        .document(validId(exerciseId))

    override suspend fun activate(
        patientId: String,
        prepared: PreparedProgram
    ) {
        require(
            prepared.exercises.isNotEmpty() &&
                    prepared.exercises.size <= 100
        )

        val document = program(
            patientId,
            prepared.id
        )

        db.runTransaction { transaction ->
            if (transaction.get(document).exists()) {
                return@runTransaction false
            }

            transaction.set(
                document,
                mapOf(
                    "sourceJson" to
                            AdaptiveJson.gson.toJson(prepared.source)
                )
            )

            prepared.exercises.forEach { compiled ->
                val plan = compiled.plan

                val state = ExerciseState(
                    exerciseId = plan.exerciseId,
                    assignedDose = plan.startingDose,
                    doseEffectiveFrom = plan.programStart
                )

                val exerciseDocument = document
                    .collection("exercises")
                    .document(validId(plan.exerciseId))

                transaction.set(
                    exerciseDocument,
                    mapOf(
                        "compiledJson" to
                                AdaptiveJson.gson.toJson(compiled),

                        "stateJson" to
                                AdaptiveJson.gson.toJson(state),

                        "stateRevision" to 0L,
                        "inputRevision" to 0L
                    )
                )
            }

            true
        }.await()
    }

    override suspend fun loadProgram(
        patientId: String,
        programId: String
    ): PreparedProgram {
        val parent = program(patientId, programId)

        val sourceJson = requireNotNull(
            parent.get().await()
                .getString("sourceJson")
        ) {
            "Program not found."
        }

        val source = AdaptiveJson.gson.fromJson(
            sourceJson,
            com.example.vitala.Recognition
                .ExercisePrescription::class.java
        )

        val exercises = parent
            .collection("exercises")
            .get()
            .await()
            .documents
            .map {
                AdaptiveJson.gson.fromJson(
                    requireNotNull(
                        it.getString("compiledJson")
                    ),
                    CompiledExercise::class.java
                )
            }
            .sortedBy { it.sourceIndex }

        require(exercises.isNotEmpty())

        return PreparedProgram(
            id = programId,
            source = source,
            exercises = exercises
        )
    }

    /**
     * A missing answer stays missing.
     * Do not fill unanswered safety questions with false.
     */
    override suspend fun saveCheckIn(
        patientId: String,
        programId: String,
        exerciseId: String,
        date: LocalDate,
        pain: Int,
        answers: Map<String, Boolean>
    ) {
        require(pain in 0..10)

        val parent = exercise(
            patientId,
            programId,
            exerciseId
        )

        val day = parent
            .collection("days")
            .document(date.toString())

        db.runTransaction { transaction ->
            val current = transaction.get(parent)

            require(current.exists()) {
                "Exercise not activated."
            }

            val previous = transaction
                .get(day)
                .getString("reportJson")
                ?.let {
                    AdaptiveJson.gson.fromJson(
                        it,
                        ExerciseDay::class.java
                    )
                }

            val report = ExerciseDay(
                exerciseId = exerciseId,
                date = date,
                preExercisePain = pain,
                session = previous?.session,
                signals = answers.toMap()
            )

            transaction.set(
                day,
                mapOf(
                    "date" to date.toString(),
                    "reportJson" to
                            AdaptiveJson.gson.toJson(report)
                ),
                SetOptions.merge()
            )

            transaction.update(
                parent,
                "inputRevision",
                (current.getLong("inputRevision") ?: 0L) + 1
            )
        }.await()
    }

    /** The dose currently assigned for an exercise (used to show "tomorrow" and Home previews). */
    override suspend fun currentState(
        patientId: String,
        programId: String,
        exerciseId: String
    ): ExerciseState? {
        val json = exercise(patientId, programId, exerciseId)
            .get().await().getString("stateJson") ?: return null
        return AdaptiveJson.gson.fromJson(json, ExerciseState::class.java)
    }

    override suspend fun evaluate(
        patientId: String,
        programId: String,
        exerciseId: String,
        today: LocalDate
    ): Evaluation {
        val parent = exercise(
            patientId,
            programId,
            exerciseId
        )

        val snapshot = parent.get().await()

        val compiled = AdaptiveJson.gson.fromJson(
            requireNotNull(
                snapshot.getString("compiledJson")
            ),
            CompiledExercise::class.java
        )

        val state = AdaptiveJson.gson.fromJson(
            requireNotNull(
                snapshot.getString("stateJson")
            ),
            ExerciseState::class.java
        )

        val reports = parent
            .collection("days")
            .whereGreaterThanOrEqualTo(
                "date",
                compiled.plan.programStart.toString()
            )
            .whereLessThanOrEqualTo(
                "date",
                today.toString()
            )
            .orderBy("date")
            .get()
            .await()
            .documents
            .map {
                AdaptiveJson.gson.fromJson(
                    requireNotNull(
                        it.getString("reportJson")
                    ),
                    ExerciseDay::class.java
                )
            }

        val recommendation = AdaptiveEngine().recommend(
            prescription = compiled.plan,
            state = state,
            reports = reports,
            today = today
        )

        return Evaluation(
            programId = programId,
            stateRevision =
                snapshot.getLong("stateRevision") ?: 0L,

            inputRevision =
                snapshot.getLong("inputRevision") ?: 0L,

            recommendation = recommendation
        )
    }

    /**
     * Applies at most one assignment per exercise per day.
     * Safety can still be evaluated again.
     */
    override suspend fun applyAssignment(
        patientId: String,
        evaluation: Evaluation
    ): Boolean {
        val result = evaluation.recommendation

        require(
            result.evaluatedFor == LocalDate.now()
        ) {
            "Only today's assignment can be applied."
        }

        require(
            result.action in setOf(
                AdaptiveAction.START,
                AdaptiveAction.MAINTAIN,
                AdaptiveAction.INCREASE,
                AdaptiveAction.DECREASE
            )
        ) {
            "This result does not provide an exercise assignment."
        }

        val nextDose = requireNotNull(
            result.nextDose
        )

        val parent = exercise(
            patientId,
            evaluation.programId,
            result.exerciseId
        )

        val day = parent
            .collection("days")
            .document(result.evaluatedFor.toString())

        return db.runTransaction { transaction ->
            val current = transaction.get(parent)

            if (
                current.getString("lastAssignedDate") ==
                result.evaluatedFor.toString()
            ) {
                return@runTransaction false
            }

            require(
                current.getLong("stateRevision") ==
                        evaluation.stateRevision &&
                        current.getLong("inputRevision") ==
                        evaluation.inputRevision
            ) {
                "The inputs changed. Evaluate again before applying."
            }

            val state = AdaptiveJson.gson.fromJson(
                requireNotNull(
                    current.getString("stateJson")
                ),
                ExerciseState::class.java
            )

            val changed =
                nextDose != state.assignedDose

            val updated = state.copy(
                assignedDose = nextDose,

                doseEffectiveFrom = if (changed) {
                    result.evaluatedFor
                } else {
                    state.doseEffectiveFrom
                },

                lastIncreaseDate =
                    if (
                        result.action ==
                        AdaptiveAction.INCREASE
                    ) {
                        result.evaluatedFor
                    } else {
                        state.lastIncreaseDate
                    }
            )

            transaction.update(
                parent,
                mapOf(
                    "stateJson" to
                            AdaptiveJson.gson.toJson(updated),

                    "stateRevision" to
                            evaluation.stateRevision + 1,

                    "lastAssignedDate" to
                            result.evaluatedFor.toString(),

                    "lastRecommendationJson" to
                            AdaptiveJson.gson.toJson(result)
                )
            )

            transaction.set(
                day,
                mapOf(
                    "assignedDoseJson" to
                            AdaptiveJson.gson.toJson(nextDose)
                ),
                SetOptions.merge()
            )

            true
        }.await()
    }

    /**
     * Records the patient-confirmed actual amount.
     * A timer alone does not confirm completion.
     */
    override suspend fun recordSession(
        patientId: String,
        programId: String,
        exerciseId: String,
        date: LocalDate,
        actualValue: Double?,
        missed: Boolean
    ) {
        val parent = exercise(
            patientId,
            programId,
            exerciseId
        )

        val day = parent
            .collection("days")
            .document(date.toString())

        db.runTransaction { transaction ->
            val current = transaction.get(parent)
            val daySnapshot = transaction.get(day)

            val report = AdaptiveJson.gson.fromJson(
                requireNotNull(
                    daySnapshot.getString("reportJson")
                ) {
                    "Save the check-in first."
                },
                ExerciseDay::class.java
            )

            require(report.session == null) {
                "A session is already recorded for this day."
            }

            val assigned = AdaptiveJson.gson.fromJson(
                requireNotNull(
                    daySnapshot.getString("assignedDoseJson")
                ) {
                    "Apply the assignment first."
                },
                ExerciseDose::class.java
            )

            require(
                missed ||
                        (
                                actualValue != null &&
                                        actualValue.isFinite() &&
                                        actualValue > 0.0
                                )
            )

            require(
                !missed || actualValue == null
            )

            val actual = if (missed) {
                null
            } else {
                ExerciseDose(
                    dimension = assigned.dimension,
                    value = requireNotNull(actualValue)
                )
            }

            val status = when {
                missed ->
                    SessionStatus.MISSED

                actual!!.value < assigned.value ->
                    SessionStatus.PARTIAL

                else ->
                    SessionStatus.COMPLETED
            }

            val updated = report.copy(
                session = SessionRecord(
                    status = status,
                    assignedDose = assigned,
                    actualDose = actual
                )
            )

            transaction.set(
                day,
                mapOf(
                    "reportJson" to
                            AdaptiveJson.gson.toJson(updated)
                ),
                SetOptions.merge()
            )

            transaction.update(
                parent,
                "inputRevision",
                (current.getLong("inputRevision") ?: 0L) + 1
            )
        }.await()
    }

    private fun validId(
        value: String
    ): String {
        return value.also {
            require(
                it.isNotBlank() &&
                        '/' !in it
            ) {
                "A real document ID is required."
            }
        }
    }
}