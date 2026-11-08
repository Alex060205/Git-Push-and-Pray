package com.example.vitala

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.vitala.Recognition.PrescriptionScreen
import com.example.vitala.data.GoogleWeatherService
import com.example.vitala.data.Patient
import com.example.vitala.data.Repositories.PatientRepository
import com.example.vitala.ui.theme.VitalaTheme
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    // Text shown on screen; Compose redraws when this changes
    private var geminiReply by mutableStateOf("Asking Gemini...")
    private var weatherText by mutableStateOf("Getting weather...")

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        AppContainer.init()   // loads demo data
//        testGemini()
//        testWeather()

        val db = PatientRepository()
        setContent {
            VitalaTheme {
                PrescriptionScreen()
//                Column {
//                    Button(onClick = {
//                        try {
//                            val patient = Patient(
//                                name = "John Doe",
//                                birthDate = "1990-01-01",
//                                gender = "Male",
//                                heightCm = 170.0,
//                                weightKg = 70.0,
//                                affectedJoints = listOf("Left knee", "Right knee")
//                            )
//                            db.savePatient(patient)
//                            Log.d("FirestoreTest", "Patient saved successfully")
//                        } catch (e: Exception) {
//                            Log.e("FirestoreTest", "Failed to save patient", e)
//                        }
//                    }) {
//                        Text("Save Patient")
//                    }
//                }
            }
        }
    }
}
/*
    /** Temporary: calls the Google Weather API once and shows the result on screen. */
    private fun testWeather() {
        val weatherService = GoogleWeatherService(BuildConfig.WEATHER_API_KEY)

        lifecycleScope.launch {
            val weather = weatherService.getCurrent()
            weatherText = if (weather == null) {
                "Weather failed. Check Logcat, filter: GoogleWeatherService"
            } else {
                "Edmonton now\n" +
                        "Condition: ${weather.condition}\n" +
                        "Temperature: ${weather.temperatureC} °C\n" +
                        "Humidity: ${weather.humidityPercent} %\n" +
                        "Pressure: ${weather.pressureHpa} hPa"
            }
            Log.d("WeatherTest", weather.toString())
        }
    }

    private fun testGemini() {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.6-flash")

        lifecycleScope.launch {
            try {
                val response = model.generateContent("Say hello in one short sentence.")
                geminiReply = response.text ?: "No text returned"
                Log.d("Gemini", geminiReply)
            } catch (e: Exception) {
                geminiReply = "Error: ${e.message}"
                Log.e("Gemini", "Call failed", e)
            }
        }
    }
}

@Composable
fun Greeting(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VitalaTheme {
        Greeting("Hello preview!")
    }
}
*/