package com.example.vitala.app

import com.example.vitala.Recognition.ExercisePrescription as Scanned
import com.example.vitala.Recognition.StopLevel
import com.example.vitala.pacing.adaptive.DoseDimension
import com.example.vitala.pacing.integration.CompiledExercise
import com.example.vitala.pacing.integration.PreparedProgram

/** Plain-words formatting for engine doses. */
object Dose {
    fun fmt(dimension: DoseDimension, v: Double): String {
        val n = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
        return when (dimension) {
            DoseDimension.MINUTES -> if (v == 1.0) "1 minute" else "$n minutes"
            DoseDimension.REPETITIONS -> if (v == 1.0) "1 repetition" else "$n repetitions"
            DoseDimension.INTENSITY -> "level $n"
            DoseDimension.RESISTANCE_KG -> "$n kg"
        }
    }

    fun fmt(dimension: String, v: Double) =
        fmt(runCatching { DoseDimension.valueOf(dimension) }.getOrDefault(DoseDimension.MINUTES), v)

    fun short(dimension: DoseDimension, v: Double): String {
        val n = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
        return when (dimension) {
            DoseDimension.MINUTES -> "$n min"
            DoseDimension.REPETITIONS -> "$n reps"
            DoseDimension.INTENSITY -> "level $n"
            DoseDimension.RESISTANCE_KG -> "$n kg"
        }
    }
}

/** Builds the OK / Check / Missing list shown on the review screen and the Plan tab (PL-2 to PL-5, PL-10). */
object PlanReview {

    fun build(rx: Scanned, program: PreparedProgram): List<ReviewItem> {
        val items = mutableListOf<ReviewItem>()
        val first: CompiledExercise? = program.exercises.firstOrNull()

        rx.exercises.forEachIndexed { i, ex ->
            val dose = listOf(ex.frequency, ex.holdOrDuration, ex.sets, ex.reps).filter { it.isNotBlank() }.joinToString(" · ")
            items += ReviewItem(
                key = "exercise_$i",
                label = ex.name,
                summary = dose.ifBlank { "Amount not found" },
                detail = buildString {
                    if (ex.setup.isNotBlank()) appendLine(ex.setup)
                    ex.steps.forEachIndexed { n, s -> appendLine("${n + 1}. $s") }
                    if (ex.pace.isNotBlank()) appendLine(ex.pace)
                },
                status = if (dose.isBlank()) ItemStatus.CHECK.name else ItemStatus.OK.name
            )
        }

        if (first != null) {
            val p = first.plan
            val dim = p.startingDose.dimension
            val lastCeiling = p.ceilings.maxOf { it.maximum }
            items += ReviewItem(
                "increase", "How to increase",
                "+${Dose.short(dim, p.progression.increment)}, after ${p.progression.requiredConsecutiveSessions} steady sessions, " +
                        "max ${Dose.short(dim, lastCeiling)}",
                rx.schedule.joinToString("\n") { listOf(it.period, it.dose, it.frequency, it.note).filter { s -> s.isNotBlank() }.joinToString(" · ") } +
                        "\nAn increase can happen at most once every ${p.progression.minimumDaysBetweenIncreases} days.",
                ItemStatus.OK.name
            )
        } else {
            items += ReviewItem("increase", "How to increase", "Not found on the plan", "", ItemStatus.MISSING.name)
        }

        val feel = (rx.generalPrecautions + rx.exercises.flatMap { it.precautions } + rx.trackingInstructions + rx.otherNotes)
            .filter { Regex("discomfort|familiar|sensitiv|symptom|ache|sore", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        items += if (feel.isNotEmpty()) {
            // PL-5: qualitative wording must be confirmed by the person, so it is always "Check".
            ReviewItem("feel", "What is OK to feel", feel.first(), feel.joinToString("\n"), ItemStatus.CHECK.name)
        } else {
            ReviewItem("feel", "What is OK to feel", "Not found on the plan", "", ItemStatus.MISSING.name)
        }

        items += if (first != null) {
            val r = first.plan.reduction
            val dim = first.plan.startingDose.dimension
            ReviewItem(
                "backup", "Backup plan",
                "${Dose.short(dim, r.decrement)} less if pain is up ${r.painIncreaseThreshold} or more (not below ${Dose.short(dim, r.minimum)})",
                first.plan.minimumDoseInstructions, ItemStatus.OK.name
            )
        } else ReviewItem("backup", "Backup plan", "Not found on the plan", "", ItemStatus.MISSING.name)

        val tolerance = (rx.adjustmentRules.map { it.condition + " " + it.action } + rx.limits + rx.otherNotes + rx.generalPrecautions)
            .filter { Regex("fatigue|daily function|daily activit|interfer", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        items += if (tolerance.isNotEmpty()) {
            ReviewItem("limits", "Fatigue and daily limits", tolerance.first(), tolerance.joinToString("\n"), ItemStatus.CHECK.name)
        } else {
            ReviewItem("limits", "Fatigue and daily limits", "Not found on the plan", "Ask your clinician about this.", ItemStatus.MISSING.name)
        }

        items += if (first != null && first.plan.incompleteInstructions.isNotBlank()) {
            ReviewItem("miss", "If you miss a day", first.plan.incompleteInstructions, first.plan.missingDataInstructions, ItemStatus.OK.name)
        } else ReviewItem("miss", "If you miss a day", "Not found on the plan", "", ItemStatus.MISSING.name)

        items += if (rx.stopRules.isNotEmpty()) {
            val urgent = rx.stopRules.sortedBy { it.level.ordinal }
            ReviewItem(
                "clinic", "When to call your clinic",
                urgent.take(3).joinToString(", ") { it.trigger.trim().trimEnd('.') },
                urgent.joinToString("\n") { "${it.level.title}: ${it.trigger}" } +
                        (if (rx.clinicContact.isNotBlank()) "\nClinic: ${rx.clinicContact}" else ""),
                ItemStatus.OK.name
            )
        } else {
            ReviewItem("clinic", "When to call your clinic", "Not found on the plan", Safety.GENERIC, ItemStatus.MISSING.name)
        }
        return items
    }

    /** First safety line shown on exercise screens. */
    fun safetyLine(rx: Scanned): String? =
        rx.stopRules.minByOrNull { it.level.ordinal }?.let { "${it.level.title}: ${it.trigger}" }

    fun contact(rx: Scanned) = buildString {
        if (rx.prescriber.isNotBlank()) append(rx.prescriber)
        if (rx.clinicContact.isNotBlank()) { if (isNotEmpty()) append(" · "); append("Clinic: ${rx.clinicContact}") }
    }

    fun hasUrgentTriggers(rx: Scanned) = rx.stopRules.any { it.level == StopLevel.URGENT_CARE || it.level == StopLevel.STOP_AND_SEEK_ASSESSMENT }
}

object Safety {
    /** SF-1, word for word. */
    const val GENERIC = "If you feel very unwell or think this is an emergency, call your local emergency number. Otherwise contact your clinic."
}
