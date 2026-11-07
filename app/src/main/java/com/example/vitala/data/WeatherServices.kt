package com.example.vitala.data

import java.time.LocalDateTime

/**
 * Gets the current weather. Returns null if it can't (no internet, bad key, API error),
 * so callers can keep going without weather.
 */
interface WeatherService {
    suspend fun getCurrent(): WeatherSnapshot?
}
