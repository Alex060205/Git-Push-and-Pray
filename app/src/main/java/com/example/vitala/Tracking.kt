package com.example.vitala

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** One per day. Weather is attached automatically, not asked. */
data class DailyCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val painLevel: Int,                  // 0..10
    val painDuration: PainDuration,
    val sleepHours: Double,
    val mood: Mood,
    val nutrition: Nutrition,
    val tookAllMedications: Boolean,
    val weather: WeatherSnapshot? = null
) {
    init {
        require(painLevel in 0..10) { "Pain level must be 0-10" }
        require(sleepHours in 0.0..24.0) { "Sleep hours must be 0-24" }
    }
}

data class WeatherSnapshot(
    val takenAt: LocalDateTime,
    val temperatureC: Double,
    val condition: String,               // e.g. "Rain"
    val humidityPercent: Int,
    val pressureHpa: Double
)

/** One exercise occurrence: what was planned, and how it went. */
data class ExerciseSession(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val scheduledTime: LocalTime,
    val intensity: Intensity,
    val items: List<ExerciseItem>,
    val status: SessionStatus = SessionStatus.PLANNED,
    val painDuring: PainFeeling? = null,
    val painAfter: PainFeeling? = null
) {
    fun markDone(during: PainFeeling, after: PainFeeling) =
        copy(status = SessionStatus.DONE, painDuring = during, painAfter = after)

    fun markSkipped() = copy(status = SessionStatus.SKIPPED)
}

/** One medication dose. AS_NEEDED doses have no scheduledTime but record pain at that moment. */
data class MedicationLog(
    val id: String = UUID.randomUUID().toString(),
    val medicationId: String,
    val date: LocalDate,
    val scheduledTime: LocalTime? = null,
    val takenAt: LocalDateTime? = null,
    val status: DoseStatus = DoseStatus.PENDING,
    val painLevelAtDose: Int? = null
) {
    init {
        require(painLevelAtDose == null || painLevelAtDose in 0..10) { "Pain level must be 0-10" }
    }

    fun markTaken(at: LocalDateTime) = copy(status = DoseStatus.TAKEN, takenAt = at)

    fun markSkipped() = copy(status = DoseStatus.SKIPPED)

    fun snooze() = copy(status = DoseStatus.SNOOZED)
}