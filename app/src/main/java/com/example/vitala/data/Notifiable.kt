package com.example.vitala.data

import java.time.LocalDate
import java.time.LocalDateTime

/** Which kind of reminder this is. Maps to an Android notification channel later. */
enum class NotificationKind { MEDICATION, EXERCISE }

/**
 * A reminder the app should show at [triggerAt].
 * Plain data with no Android types, so the scheduler (AlarmManager / WorkManager)
 * can be added later without touching the models.
 * @param key Stable unique key. The same reminder always gets the same key,
 *            so rescheduling replaces it instead of creating a duplicate.
 * @param kind Medication or exercise
 * @param title Notification title
 * @param body Notification text
 * @param triggerAt When to show it
 * @property notificationId Int id for NotificationManager, derived from [key]
 */
data class NotificationRequest(
    val key: String,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val triggerAt: LocalDateTime
) {
    val notificationId: Int
        get() = key.hashCode()
}

/**
 * Parent for anything that can produce reminders (Medication, ExerciseSession).
 */
interface Notifiable {

    /**
     * Returns the reminders that fall on the given date.
     * @param date Date to check
     * @return List of reminders (empty if none)
     */
    fun notificationsFor(date: LocalDate): List<NotificationRequest>

    /**
     * Returns today's reminders that haven't fired yet, earliest first.
     * @param now Current date and time
     * @return List of reminders still to come today (empty if none)
     */
    fun upcomingNotifications(now: LocalDateTime): List<NotificationRequest> =
        notificationsFor(now.toLocalDate())
            .filter { it.triggerAt.isAfter(now) }
            .sortedBy { it.triggerAt }
}