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

@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    OptionCard(selected == value, label, onClick = { onPick(value) })
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(3.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
    )
}

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
