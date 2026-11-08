package com.example.vitala.data

import java.time.LocalDate
import java.util.UUID
import com.google.firebase.firestore.Exclude

/**
 * Recovery answer, only asked while a change or flare is being evaluated.
 * @param time Only when [status] is RETURNED: roughly when symptoms got back to the usual range.
 */
data class Recovery(val status: RecoveryStatus, val time: RecoveryTime? = null) {
    init {
        require(time == null || status == RecoveryStatus.RETURNED) { "Recovery time only applies once recovered" }
    }

    val label: String
        get() = if (status == RecoveryStatus.RETURNED && time != null) "${status.label} (${time.label})" else status.label
}

/**
 * One per day (stored under the date, so saving the same day again replaces it).
 * The four check-in questions are painLevel, fatigue, interference and recovery.
 * @param painLevel Pain today, 0-10
 * @param fatigue Fatigue today, 0-10
 * @param interference Interference with usual daily activities over the previous 24 hours,
 *                     0 = none, 10 = completely prevented them
 * @param recovery Only while a change or flare is being evaluated; null otherwise
 * The remaining fields are from the earlier check-in design and are optional.
 */
data class DailyCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val painLevel: Int,
    val fatigue: Int,
    val interference: Int,
    val recovery: Recovery? = null,
    val painDuration: PainDuration? = null,
    val sleepHours: Double? = null,
    val mood: Mood? = null,
    val nutrition: Nutrition? = null,
    val tookAllMedications: Boolean? = null,
    val weather: WeatherSnapshot? = null,
    val exerciseIntensity: Intensity? = null
) {
    init {
        require(painLevel in 0..10) { "Pain level must be 0-10" }
        require(fatigue in 0..10) { "Fatigue must be 0-10" }
        require(interference in 0..10) { "Interference must be 0-10" }
        sleepHours?.let { require(it in 0.0..24.0) { "Sleep hours must be 0-24" } }
    }
    constructor(
        date: LocalDate,
        painLevel: Int,
        fatigue: Int,
        interference: Int,
        recovered: Boolean,
        id: String = UUID.randomUUID().toString()
    ) : this(
        id = id,
        date = date,
        painLevel = painLevel,
        fatigue = fatigue,
        interference = interference,
        recovery = Recovery(
            status = if (recovered) {
                RecoveryStatus.RETURNED
            } else {
                RecoveryStatus.UNSURE
            }
        )
    )

    @get:Exclude
    val recovered: Boolean?
        get() = when (recovery?.status) {
            RecoveryStatus.RETURNED -> true
            RecoveryStatus.STILL_ABOVE -> false
            RecoveryStatus.UNSURE, null -> null
        }
}
