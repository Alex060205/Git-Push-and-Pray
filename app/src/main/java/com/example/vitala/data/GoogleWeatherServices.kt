package com.example.vitala.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

/**
 * Google Maps Platform Weather API (currentConditions:lookup).
 * Metric units by default: temperature in °C, pressure in millibars (= hPa).
 * @param apiKey Maps API key with the Weather API enabled
 * @param latitude Fixed location latitude (default: Edmonton)
 * @param longitude Fixed location longitude (default: Edmonton)
 */
class GoogleWeatherService(
    private val apiKey: String,
    private val latitude: Double = EDMONTON_LAT,
    private val longitude: Double = EDMONTON_LNG
) : WeatherService {

    /**
     * Gets the current weather at the fixed location from Google.
     * Runs on a background thread; gives up after 10 seconds.
     * @return The current weather, or null if the key is missing, there is no internet,
     *          Google returns an error, or the response is missing a field
     */

    override suspend fun getCurrent(): WeatherSnapshot? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "No WEATHER_API_KEY set in local.properties")
            return@withContext null
        }
        val url = URL(
            "https://weather.googleapis.com/v1/currentConditions:lookup" +
                    "?key=$apiKey&location.latitude=$latitude&location.longitude=$longitude"
        )
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "Weather API returned $code: $error")
                return@withContext null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseCurrentConditions(body)
        } catch (e: Exception) {
            Log.w(TAG, "Weather request failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TAG = "GoogleWeatherService"
        private const val TIMEOUT_MS = 10_000
        const val EDMONTON_LAT = 53.5461
        const val EDMONTON_LNG = -113.4938

        /**
         * Reads the 4 fields Vitala uses from a currentConditions:lookup response.
         * Throws if a required field is missing (caught in getCurrent()).
         * @return A weather snapshot with temperature, simplified condition, humidity
         *          and pressure, timestamped now
         * @throws org.json.JSONException If temperature, humidity or pressure is missing
         *          (caught in getCurrent())
         */
        internal fun parseCurrentConditions(json: String): WeatherSnapshot {
            val root = JSONObject(json)
            val type = root.optJSONObject("weatherCondition")?.optString("type").orEmpty()
            return WeatherSnapshot(
                takenAt = LocalDateTime.now(),
                temperatureC = root.getJSONObject("temperature").getDouble("degrees"),
                condition = simplifyCondition(type),
                humidityPercent = root.getInt("relativeHumidity"),
                pressureHpa = root.getJSONObject("airPressure").getDouble("meanSeaLevelMillibars")
            )
        }

        /**
         * Maps Google's ~40 condition types to one of: Sunny, Rainy, Snowy, Cloudy.
         * Order matters: SNOWSTORM and RAIN_AND_SNOW are Snowy, WIND_AND_RAIN is Rainy.
         * Thunderstorms and hail count as Rainy; windy and unknown count as Cloudy.
         * @param googleType Google's condition type, e.g. "LIGHT_RAIN_SHOWERS"
         * @return "Sunny", "Rainy", "Snowy" or "Cloudy"
         *          ("Cloudy" if the type is empty or not recognized)
         */
        internal fun simplifyCondition(googleType: String): String {
            val t = googleType.uppercase()
            return when {
                "SNOW" in t -> "Snowy"
                "RAIN" in t || "SHOWER" in t || "THUNDER" in t || "HAIL" in t -> "Rainy"
                "CLEAR" in t -> "Sunny"
                else -> "Cloudy"   // CLOUDY, PARTLY_CLOUDY, WINDY, TYPE_UNSPECIFIED
            }
        }
    }
}