package com.example.vitala.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/*
 * App-level records that the existing backend does not cover (profile, settings, conditions,
 * plan versions, medicines, context events...). Everything is plain data with ISO strings
 * ("yyyy-MM-dd", "HH:mm") so it can be stored as JSON in Firestore without custom adapters.
 */

data class Profile(
    val firstName: String = "",
    val lastName: String = "",
    val medicalProvider: String = "",
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val healthUpdated: String? = null,
    /** 0 = about you, 1 = set up your day, 2 = condition, 3 = plan, 4 = reminders, 5 = medicines, 6 = calendar, 7 = done */
    val onboardingStep: Int = 0
) {
    val fullName get() = "$firstName $lastName".trim()
    val onboarded get() = onboardingStep >= 7
}

enum class TextSizeChoice(val label: String, val scale: Float) {
    NORMAL("Normal", 1.0f), LARGE("Large", 1.2f), EXTRA_LARGE("Extra large", 1.4f)
}

data class Settings(
    val textSize: String = TextSizeChoice.LARGE.name,
    val highContrast: Boolean = false,
    val usualExerciseTime: String = "10:30",
    val exerciseRemindersOn: Boolean = true,
    val leadMinutes: Int = 15,
    val medRemindersOn: Boolean = true,
    val checkInRemindersOn: Boolean = true,
    val checkInTime: String = "19:00",
    val weeklyOn: Boolean = true,
    val weeklyDay: Int = 7,            // ISO day of week, 7 = Sunday
    val weeklyTime: String = "10:00",
    val quietOn: Boolean = true,
    val quietStart: String = "21:00",
    val quietEnd: String = "07:00",
    val showMedNames: Boolean = false, // NT-1: lock-screen text hides the medicine name by default
    val calendarLinked: Boolean = false,
    val calendarSource: String = "",
    val suggestTimes: Boolean = true,
    val pegPostponedUntil: String? = null,
    /** Exercise reminder times, one per plan exercise (PL-13). */
    val exerciseTimes: List<ExerciseTime> = emptyList(),
    /** One-day time changes accepted from "Suggested time": date -> (exerciseId -> time). */
    val dayTimes: Map<String, Map<String, String>> = emptyMap()
) {
    val textSizeChoice get() = TextSizeChoice.entries.firstOrNull { it.name == textSize } ?: TextSizeChoice.LARGE
}

data class ExerciseTime(
    val exerciseName: String,
    val exerciseId: String = "",
    val time: String = "10:30",
    val leadMinutes: Int = 15,
    val enabled: Boolean = true
)

data class ConditionRec(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val diagnosis: String = "",
    val archived: Boolean = false,
    val createdAt: String = LocalDate.now().toString()
)

/** One saved version of a condition's plan. The engine data itself lives under adaptivePrograms/{programId}. */
data class PlanVersionRec(
    val id: String = "",               // = programId
    val conditionId: String = "",
    val version: Int = 1,
    val doctor: String = "",
    val planDate: String = "",         // date approved, as written on the plan
    val startDate: String = "",
    val savedOn: String = LocalDate.now().toString(),
    val replacedOn: String? = null,
    val source: String = "photo",      // photo | file | manual
    val imagePaths: List<String> = emptyList(),
    val current: Boolean = true,
    val items: List<ReviewItem> = emptyList()
) {
    val incomplete get() = items.any { it.status == ItemStatus.MISSING.name }
}

enum class ItemStatus { OK, CHECK, MISSING }

data class ReviewItem(
    val key: String = "",
    val label: String = "",
    val summary: String = "",
    val detail: String = "",
    val status: String = ItemStatus.OK.name
)

data class MedicationRec(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val dose: String = "",
    val withFood: Boolean = false,
    val asNeeded: Boolean = false,
    val times: List<String> = listOf("08:00"),
    val remindersOn: Boolean = true,
    val important: Boolean = false,    // may ring in quiet hours
    val stopped: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null
) {
    val instructions get() = if (withFood) "with food" else ""
}

enum class MedAction { TAKEN, SKIPPED, SNOOZED }

data class MedEvent(
    val id: String = UUID.randomUUID().toString(),
    val medicationId: String = "",
    val date: String = LocalDate.now().toString(),
    val scheduledTime: String? = null,
    val action: String = MedAction.TAKEN.name,
    val at: String = java.time.LocalDateTime.now().toString()
)

enum class ContextType(val label: String) {
    HARD_ACTIVITY("Unusually hard activity"),
    ILL("Feeling ill"),
    MEDICINE_CHANGED("Medicine changed"),
    MORE_PAIN_MEDICINE("Used more pain medicine than usual")
}

data class ContextEventRec(
    val id: String = UUID.randomUUID().toString(),
    val date: String = LocalDate.now().toString(),
    val types: List<String> = emptyList(),
    val note: String = ""
)

/** What was suggested/chosen for a day (ER-11, ER-12). */
data class RecommendationRec(
    val date: String = LocalDate.now().toString(),
    val level: String = "",
    val reason: String = "",
    val ruleIds: List<String> = emptyList(),
    val programIds: List<String> = emptyList(),
    val chosenLevel: String? = null,
    val override: Boolean = false
)

data class EditLogRec(
    val id: String = UUID.randomUUID().toString(),
    val date: String = "",
    val editedAt: String = java.time.LocalDateTime.now().toString(),
    val before: String = "",
    val after: String = ""
)

enum class Level(val label: String) { PLANNED("Planned"), SHORTER("Shorter"), REST("Rest") }

/** One exercise's answer from today's pre-session evaluation. Stored so Home survives a restart. */
data class DayExercise(
    val programId: String = "",
    val exerciseId: String = "",
    val name: String = "",
    val dimension: String = "MINUTES",
    val action: String = "",
    val doseValue: Double? = null,
    val minimumValue: Double? = null,
    val reason: String = "",
    val instructions: String = "",
    val ruleId: String = "",
    val clinicContact: String = "",
    val note: String = "",
    val actualValue: Double? = null
)

/** Today's working state. Doc id = date. */
data class DayRec(
    val date: String = LocalDate.now().toString(),
    val pain: Int? = null,
    val exercises: List<DayExercise> = emptyList(),
    val suggestedTime: String = "",
    val levelChosen: String? = null,
    val applied: Boolean = false,
    val timerSeconds: Int? = null,
    val logged: Boolean = false,
    val symptomResponse: String? = null,
    val stopReason: String? = null,
    val reviewConfirmed: Boolean = false
) {
    val blocked get() = exercises.any { it.action in BLOCKING }
    companion object {
        val BLOCKING = setOf("URGENT", "STOP_AND_ASSESS", "PAUSE", "REVIEW", "NEEDS_INFORMATION", "NOT_STARTED")
    }
}

fun String.toTime(): LocalTime = LocalTime.parse(this)
fun LocalTime.hhmm(): String = "%02d:%02d".format(hour, minute)
fun Int.toDay(): DayOfWeek = DayOfWeek.of(this)
