package com.example.vitala.data

import java.time.LocalDate
import java.util.UUID

/**
 * One per day. Weather is attached automatically, not asked.
 * @param id Unique identifier
 * @param date Date of the check-in
 * @param painLevel average pain level from the past 24 hours, 0 as none, and 10 as completely worse
 * @param fatigue average fatigue level from the past 24 hours, 0 as none, and 10 as completely tired
 * @param interference average interference with usual daily activities from the past 24 hours, 0 as none, and 10 as completely avoided
 * @param recovered Whether the patient recovered today.
 * @constructor Creates a new daily check-in object with the given parameters
 */
data class DailyCheckIn(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val painLevel: Int,                  // 0..10
    val fatigue: Int,                     // 0..10
    val interference: Int,
    val recovered: Boolean = false,
) {
    init {
        require(painLevel in 0..10) { "Pain level must be 0-10" }
    }
}