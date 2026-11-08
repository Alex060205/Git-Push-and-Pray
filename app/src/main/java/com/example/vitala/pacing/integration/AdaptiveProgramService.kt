package com.example.vitala.pacing.integration

import com.example.vitala.Recognition.ExercisePrescription as ScannedPrescription
import com.example.vitala.Recognition.PrescriptionExtractor
import java.util.UUID

class AdaptiveProgramService(
    private val extractor: PrescriptionExtractor =
        PrescriptionExtractor()
) {

    suspend fun prepare(
        source: ScannedPrescription
    ): PreparedProgram {
        require(source.exercises.isNotEmpty()) {
            "No exercises found."
        }

        require(source.unreadable.isEmpty()) {
            "Resolve unreadable prescription fields first."
        }

        val programId = UUID.randomUUID().toString()

        val exercises = source.exercises.indices.map {
                index ->

            extractor.compileAdaptive(
                source = source,
                exerciseIndex = index,
                exerciseId = "$programId-$index"
            )
        }

        return PreparedProgram(
            id = programId,
            source = source,
            exercises = exercises
        )
    }
}