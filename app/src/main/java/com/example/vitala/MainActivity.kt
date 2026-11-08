package com.example.vitala

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.vitala.data.GoogleWeatherService
import com.example.vitala.data.HealthLogRepository
import com.example.vitala.data.Patient
import com.example.vitala.data.PrescribedExercise
import com.example.vitala.report.ReportPdf
import com.example.vitala.report.ReportSample
import com.example.vitala.report.ReportSaver
import com.example.vitala.report.loadReport
import com.example.vitala.ui.theme.VitalaTheme
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek

/** Temporary: true = report data goes through Firestore; false = built straight from sample data. */
private const val USE_FIREBASE = true

class MainActivity : ComponentActivity() {

    // Text shown on screen; Compose redraws when this changes
    private var geminiReply by mutableStateOf("Asking Gemini...")
    private var weatherText by mutableStateOf("Getting weather...")

    // Temporary: report test
    private var reportStatus by mutableStateOf("Building report...")
    private var reportPages by mutableStateOf<List<Bitmap>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        AppContainer.init()   // loads demo data
//        testGemini()
//        testWeather()
        testReport()

        setContent {
            VitalaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ReportPreview(
                        status = reportStatus,
                        pages = reportPages,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    /**
     * Temporary: builds the doctor report from sample data, saves it to Downloads/Vitala,
     * and renders every page on screen so it can be checked without a PDF viewer.
     */
    private fun testReport() {
        lifecycleScope.launch {
            try {
                val (location, pages) = withContext(Dispatchers.IO) {
                    val patient = samplePatient()
                    val data = if (USE_FIREBASE) {
                        // Round trip: write the sample records to Firestore, then read them back.
                        val repo = HealthLogRepository()
                        ReportSample.upload(repo, patient.id)
                        repo.loadReport(patient, ReportSample.ANCHOR, sampleNotes = listOf(ReportSample.WARNING))
                    } else {
                        ReportSample.build(patient)
                    }
                    val fileName = ReportPdf.fileName(data.to)

                    // Copy for the on-screen preview
                    val previewFile = File(cacheDir, fileName)
                    previewFile.outputStream().use { ReportPdf.write(data, it) }

                    // Copy the user can find in Downloads › Vitala
                    val saved = ReportSaver.save(this@MainActivity, fileName) { ReportPdf.write(data, it) }

                    saved.location to renderPages(previewFile)
                }
                reportPages = pages
                reportStatus = "Saved to $location (${pages.size} pages)"
                Log.d("ReportTest", reportStatus)
            } catch (e: Exception) {
                reportStatus = "Report failed: ${e.message}. Check Logcat, filter: ReportTest"
                Log.e("ReportTest", "Report failed", e)
            }
        }
    }

    /** Draws each PDF page into a bitmap at 2x so the text stays sharp on screen. */
    private fun renderPages(file: File): List<Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { page ->
                        Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }

    /** Temporary test patient. Replace with the real one from Firebase. */
    private fun samplePatient() = Patient(
        id = "test-patient",   // fixed, so repeated runs overwrite the same Firestore documents
        name = "Alex Rivera",
        birthDate = "1968-04-22",
        gender = "Female",
        heightCm = 165.0,
        weightKg = 72.0,
        affectedJoints = listOf("Lower back", "Right hip"),
        exercise = listOf(
            PrescribedExercise(name = "Pelvic tilts", sets = 2, reps = 10,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
            PrescribedExercise(name = "Cat-cow", sets = 2, reps = 8,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            PrescribedExercise(name = "Bridges", sets = 3, reps = 10,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
            PrescribedExercise(name = "Walk", durationMinutes = 15,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
        )
    )

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

/** Temporary: scrollable list of the report's pages. */
@Composable
fun ReportPreview(status: String, pages: List<Bitmap>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFFE6E3DD)),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(status) }
        items(pages) { page ->
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = "Report page",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
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
