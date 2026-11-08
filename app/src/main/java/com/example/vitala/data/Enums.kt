package com.example.vitala.data

/** Pain before / during / after an exercise (matches the "How did your exercise go?" screen). */
enum class PainFeeling { NONE, SOME, A_LOT }

/** How long the pain lasted today. */
enum class PainDuration { NONE, A_FEW_MOMENTS, A_FEW_HOURS, MOST_OF_THE_DAY, ALL_DAY }

enum class Mood { BAD, OKAY, GOOD }

enum class Nutrition { POOR, OKAY, GOOD }

/** The three choices the patient picks from each day. */
enum class Intensity { REST, GENTLE, FULL}

enum class SessionStatus { PLANNED, DONE, OVERDONE, CUT_SHORT, SKIPPED }
enum class DoseStatus { PENDING, TAKEN, SKIPPED, SNOOZED }

/** SCHEDULED = fixed times; AS_NEEDED = "only when pain is high". */
enum class MedicationType { SCHEDULED, AS_NEEDED }

enum class MetricsSource { ACCOUNT_CREATION, DOCTOR_VISIT }

enum class Confidence { LOW, MEDIUM, HIGH }

enum class TrendDirection { BETTER, WORSE, STABLE }

enum class InsightFactor { SLEEP, EXERCISE, WEATHER, MEDICATION, MOOD, NUTRITION }

/** Why a session was skipped or cut short. PLACEHOLDER list: replace with the app's real options. */
enum class StopReason(val label: String) {
    PAIN("Pain"), FATIGUE("Fatigue"), NO_TIME("No time"), OTHER("Other")
}

/** Asked immediately after each session. */
enum class SymptomResponse(val label: String) {
    WITHIN_RANGE("Familiar, within range"),
    BEYOND_RANGE("Familiar, beyond range"),
    NEW_OR_DIFFERENT("New or different")
}

/** Asked at the daily check-in while a change or flare is being evaluated. */
enum class RecoveryStatus(val label: String) {
    RETURNED("Back to usual range"), STILL_ABOVE("Still above usual range"), UNSURE("Unsure")
}

/** If recovered, roughly when. */
enum class RecoveryTime(val label: String) { SAME_DAY("same day"), NEXT_DAY("next day"), LATER("later") }
