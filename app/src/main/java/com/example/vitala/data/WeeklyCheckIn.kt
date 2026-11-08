package com.example.vitala.data

import com.google.firebase.firestore.DocumentId
import java.util.UUID


/**
 * One per week.
 * @param id Unique identifier
 * @param averagePain Average pain level from the past 7 days, 0 as none, and 10 as completely worse
 * @param enjoymentOfLife How much did you enjoy your life today?
 * @param notes Any other notes, what has changed.
 */
class WeeklyCheckIn(
    @DocumentId val id: String = UUID.randomUUID().toString(),
    ) {
    var averagePain: Int = 0
    var enjoymentOfLife: String = ""
    var notes: String = ""
}
