package com.example.vitala.data

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID


/**
 * A medication.
 * @param id Unique identifier
 * @param name Name of the medication
 * @param dose Dosage of the medication
 *  @param type Type of the medication
 *  @param times Times of the medication
 *  @param notes Notes about the medication
 *  @constructor Creates a new medication object with the given parameters
 */
data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dose: String,                         // e.g. "1 tablet"
    val type: MedicationType,
    val times: List<LocalTime> = emptyList(), // only for SCHEDULED
    val notes: String = ""                    // e.g. "with food"
) : Notifiable {
    init {
        require(type == MedicationType.AS_NEEDED || times.isNotEmpty()) {
            "Scheduled medication needs at least one time"
        }
    }

    /**
     * Returns one reminder per schedule time on the given date
     * S_NEEDED medications return nothing: this is for meds that are too high
     * Does not know whether a dose was already taken: the scheduler should skip
     * times that already have a TAKEN ir SKIPPED MedicationLog
     * @param date Date ti check
     * @return List of reminders (empty for AS_NEEDED)
     */
    override fun notificationsFor(date: LocalDate): List<NotificationRequest> {
        if (type == MedicationType.AS_NEEDED) return emptyList()
        return times.map { time ->
            NotificationRequest(
                key = "med-$id-$date-$time",
                kind = NotificationKind.MEDICATION,
                title = "Time for $name",
                body = if (notes.isBlank()) dose else "$dose · $notes",
                triggerAt = date.atTime(time)
            )

        }
    }
}