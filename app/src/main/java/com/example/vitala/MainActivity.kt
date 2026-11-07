package com.example.vitala

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.example.vitala.ui.theme.VitalaTheme
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Text shown on screen; Compose redraws when this changes
    private var geminiReply by mutableStateOf("Asking Gemini...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppContainer.init()   // loads demo data
        testGemini()

        setContent {
            VitalaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        message = geminiReply,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
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