package com.example.vitala.data

import java.time.LocalDateTime

/**
 * A snapshot of the weather at a given time.
 * @param takenAt Time of the snapshot
 * @param temperatureC Temperature in Celsius
 * @param condition Weather condition, e.g. "Rain"
 * @param humidityPercent Humidity as a percentage
 * @param pressureHpa Pressure in hectopascals
 * @constructor Creates a new weather snapshot object with the given parameters
 */
data class WeatherSnapshot(
    val takenAt: LocalDateTime,
    val temperatureC: Double,
    val condition: String,      // e.g. "Rain"
    val humidityPercent: Int,
    val pressureHpa: Double
)
