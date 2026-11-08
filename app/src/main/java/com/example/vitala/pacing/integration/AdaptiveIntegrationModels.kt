package com.example.vitala.pacing.integration

import com.example.vitala.Recognition.ExercisePrescription as ScannedPrescription
import com.example.vitala.pacing.adaptive.*
import com.google.gson.*
import java.lang.reflect.Type
import java.time.LocalDate

data class CompiledExercise(
    val sourceIndex: Int,
    val plan: ExercisePrescription,
    val questions: Map<String, String>
)

data class PreparedProgram(
    val id: String,
    val source: ScannedPrescription,
    val exercises: List<CompiledExercise>
)

data class Evaluation(
    val programId: String,
    val stateRevision: Long,
    val inputRevision: Long,
    val recommendation: AdaptiveRecommendation
)

object AdaptiveJson {

    private class DateAdapter :
        JsonSerializer<LocalDate>,
        JsonDeserializer<LocalDate> {

        override fun serialize(
            value: LocalDate,
            type: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return JsonPrimitive(value.toString())
        }

        override fun deserialize(
            value: JsonElement,
            type: Type,
            context: JsonDeserializationContext
        ): LocalDate {
            return LocalDate.parse(value.asString)
        }
    }

    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(
            LocalDate::class.java,
            DateAdapter()
        )
        .create()
}