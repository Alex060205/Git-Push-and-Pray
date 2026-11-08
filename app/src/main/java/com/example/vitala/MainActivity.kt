package com.example.vitala

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.vitala.app.Reminders
import com.example.vitala.ui.VitalaApp

class MainActivity : ComponentActivity() {

    /** Set when the app is opened from a reminder; handled once the person is signed in and set up. */
    private var openRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.createChannels(this)
        openRequest = intent?.getStringExtra(Reminders.EXTRA_OPEN)

        setContent {
            VitalaApp(openRequest = openRequest, onOpenHandled = { openRequest = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest = intent.getStringExtra(Reminders.EXTRA_OPEN)
    }
}
