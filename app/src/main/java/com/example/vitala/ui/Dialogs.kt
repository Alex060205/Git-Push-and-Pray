package com.example.vitala.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vitala.app.ContextType
import com.example.vitala.app.TextSizeChoice
import com.example.vitala.data.RecoveryTime

@Composable
fun ChoiceDialog(
    title: String, options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit,
    descriptions: Map<String, String> = emptyMap()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    OptionCard(selected == value, label, onClick = { onPick(value) }, subtitle = descriptions[value])
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
    )
}

/** Reminder lead-time choices: short bold title, plain description underneath. */
val LEAD_OPTIONS: List<Pair<String, String>> = listOf(5, 10, 15, 30, 60).map { it.toString() to if (it == 60) "1 hour before" else "$it minutes before" }
val LEAD_DESCRIPTIONS: Map<String, String> = mapOf(
    "5" to "A quick nudge right before you start.",
    "10" to "A little time to get ready.",
    "15" to "Time to finish what you're doing and get set up.",
    "30" to "Plenty of notice to plan around it.",
    "60" to "An early heads-up for your session."
)

@Composable
fun NumberDialog(title: String, unit: String, initial: String, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LabeledField("Number ($unit)", text, { text = it.filter { c -> c.isDigit() || c == '.' } }, keyboard = KeyboardType.Decimal) },
        confirmButton = { TextButton(onClick = { text.toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirm) }) { Text("Save", style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
    )
}

val TextSizeChoice.description: String
    get() = when (this) {
        TextSizeChoice.NORMAL -> "Standard text size."
        TextSizeChoice.LARGE -> "A bit bigger and easier to read."
        TextSizeChoice.EXTRA_LARGE -> "Much bigger, for the easiest reading."
    }

/** "When did it settle?" choices. */
val RECOVERY_TIMES: List<Triple<RecoveryTime, String, String>> = listOf(
    Triple(RecoveryTime.SAME_DAY, "Same day", "It settled on the day of the session."),
    Triple(RecoveryTime.NEXT_DAY, "Next day", "It settled by the next day."),
    Triple(RecoveryTime.LATER, "Later", "It took more than a day to settle.")
)

val ContextType.shortLabel: String
    get() = when (this) {
        ContextType.HARD_ACTIVITY -> "Hard activity"
        ContextType.ILL -> "Feeling ill"
        ContextType.MEDICINE_CHANGED -> "Medicine changed"
        ContextType.MORE_PAIN_MEDICINE -> "More pain medicine"
    }

val ContextType.description: String
    get() = when (this) {
        ContextType.HARD_ACTIVITY -> "I did something unusually hard or tiring."
        ContextType.ILL -> "A cold, flu or another illness."
        ContextType.MEDICINE_CHANGED -> "I started, stopped or changed a medicine."
        ContextType.MORE_PAIN_MEDICINE -> "I used more pain medicine than usual."
    }
