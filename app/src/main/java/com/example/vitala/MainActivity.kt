package com.example.vitala

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.vitala.Recognition.PrescriptionScreen
import com.example.vitala.ui.theme.VitalaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VitalaTheme {
                PrescriptionScreen()
            }
        }
    }
}