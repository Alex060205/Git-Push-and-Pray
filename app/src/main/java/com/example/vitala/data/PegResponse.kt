package com.example.vitala.data

import java.time.LocalDate

/**
 * Weekly PEG questionnaire, each item 0-10 over the past week.
 * @param pain Pain on average (0 no pain, 10 as bad as you can imagine)
 * @param enjoyment How much pain interfered with enjoyment of life (0 not at all, 10 completely)
 * @param activity How much pain interfered with general activity (0 not at all, 10 completely)
 */
data class PegResponse(val date: LocalDate, val pain: Int, val enjoyment: Int, val activity: Int) {
    init {
        require(pain in 0..10 && enjoyment in 0..10 && activity in 0..10) { "PEG items must be 0-10" }
    }

    /** Average of the three items. */
    val score: Double get() = (pain + enjoyment + activity) / 3.0
}
