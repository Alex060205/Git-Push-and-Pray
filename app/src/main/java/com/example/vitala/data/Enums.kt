package com.example.vitala.data

/** Pain during / after an exercise (matches the "How did your exercise go?" screen). */
enum class PainFeeling { NONE, SOME, A_LOT }

/** How long the pain lasted today. */
enum class PainDuration { NONE, A_FEW_MOMENTS, A_FEW_HOURS, MOST_OF_THE_DAY, ALL_DAY }

enum class Mood { BAD, OKAY, GOOD }

enum class Nutrition { POOR, OKAY, GOOD }

/** The three choices the patient picks from each day. */
enum class Intensity { REST, GENTLE, FULL }

enum class SessionStatus { PLANNED, DONE, SKIPPED }

enum class DoseStatus { PENDING, TAKEN, SKIPPED, SNOOZED }

/** SCHEDULED = fixed times; AS_NEEDED = "only when pain is high". */
enum class MedicationType { SCHEDULED, AS_NEEDED }

enum class MetricsSource { ACCOUNT_CREATION, DOCTOR_VISIT }

enum class Confidence { LOW, MEDIUM, HIGH }

enum class TrendDirection { BETTER, WORSE, STABLE }

enum class InsightFactor { SLEEP, EXERCISE, WEATHER, MEDICATION, MOOD, NUTRITION }