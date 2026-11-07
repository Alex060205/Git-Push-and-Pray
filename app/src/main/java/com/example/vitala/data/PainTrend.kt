package com.example.vitala.data

import java.time.LocalDate

/**
 * Overall pain trend for the History screen ("Getting better").
 * @param from Start date of the trend
 * @param to End date of the trend
 * @param averageFirstHalf Average pain in the first half of the trend
 * @param averageSecondHalf Average pain in the second half of the trend
 * @param direction Trend direction
 * @constructor Creates a new pain trend object with the given parameters
 */
data class PainTrend(
    val from: LocalDate,
    val to: LocalDate,
    val averageFirstHalf: Double,
    val averageSecondHalf: Double,
    val direction: TrendDirection
)