package com.example.vitala.Recognition

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PrescriptionScreen(vm: PrescriptionViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> vm.onPhotoResult(success) }

    // System photo picker: no storage permission needed; the user picks one image
    // and the app only gets access to that image.
    // Multi-select: a prescription often spans several pages.
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ScanUiState.MAX_PAGES)
    ) { uris -> vm.onImagesPicked(uris) }

    // System file picker, limited to PDF and plain text. No permission needed.
    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> vm.onDocumentPicked(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // MainActivity draws edge-to-edge; keep the buttons out from under
            // the status bar / camera cutout and the results above the nav bar.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    try {
                        takePicture.launch(vm.createPhotoUri())
                    } catch (e: ActivityNotFoundException) {
                        vm.onCameraUnavailable()
                    }
                },
                enabled = state.canAddPages,
                modifier = Modifier.weight(1f)
            ) { Text(if (state.pages.isEmpty()) "Camera" else "+ Photo") }

            OutlinedButton(
                onClick = {
                    pickImages.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = state.canAddPages,
                modifier = Modifier.weight(1f)
            ) { Text("Images") }

            OutlinedButton(
                onClick = { pickDocument.launch(arrayOf("application/pdf", "text/plain")) },
                enabled = !state.isAnalyzing,
                modifier = Modifier.weight(1f)
            ) { Text("File") }
        }

        if (state.pages.isNotEmpty()) {
            PageStrip(state.pages, enabled = !state.isAnalyzing, onRemove = vm::removePage)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = vm::analyzePages,
                    enabled = !state.isAnalyzing,
                    modifier = Modifier.weight(1f)
                ) {
                    val n = state.pages.size
                    Text(if (n == 1) "Read prescription" else "Read $n pages")
                }
                TextButton(onClick = vm::clearPages, enabled = !state.isAnalyzing) { Text("Clear") }
            }
            Text(
                "Add a photo for each page, then tap Read." +
                        if (state.pages.size >= ScanUiState.MAX_PAGES) " Page limit reached." else "",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Original stays visible so every number can be checked against it.
        state.preview?.let { preview ->
            val image = remember(preview) { preview.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = "The prescription",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
            )
        }
        state.previewCaption?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        state.sourceText?.let { text ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }

        if (state.isAnalyzing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Text("Reading the prescription…")
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        state.prescription?.let { PrescriptionDetails(it) }
    }
}

/** Thumbnails of the collected pages, in order, each with a remove button. */
@Composable
private fun PageStrip(pages: List<ScanPage>, enabled: Boolean, onRemove: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(pages, key = { _, p -> p.path }) { i, page ->
            Box {
                val image = remember(page.thumbnail) { page.thumbnail.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "Page ${i + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 96.dp, height = 128.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (enabled) {
                    IconButton(
                        onClick = { onRemove(page.path) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                    ) {
                        Text(
                            "✕",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrescriptionDetails(rx: ExercisePrescription) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Extracted automatically. Check every rule and number against the original.",
            style = MaterialTheme.typography.bodySmall
        )

        if (rx.unreadable.isNotEmpty()) {
            Bullets("Could not read clearly", rx.unreadable, warning = true)
        }

        // Safety first: most urgent level at the top.
        if (rx.stopRules.isNotEmpty()) {
            Heading("When to stop or get help", warning = true)
            rx.stopRules.groupBy { it.level }.toSortedMap(compareBy { it.ordinal })
                .forEach { (level, rules) -> Bullets(level.title, rules.map { it.trigger }, warning = true) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Heading(rx.programName.ifEmpty { "Program" })
            Field("Area", rx.bodyArea)
            Field("Context", rx.clinicalContext)
            Field("Goals", rx.treatmentGoals)
            Field("Start", rx.startDate)
            Field("Review", rx.reviewPlan)
            Field("Prescriber", rx.prescriber)
            Field("Contact", rx.clinicContact)
            Field("Approved", rx.dateApproved)
        }

        if (rx.schedule.isNotEmpty()) {
            Heading("Schedule")
            rx.schedule.forEach { p ->
                Text(listOf(p.period, p.dose, p.frequency, p.note)
                    .filter { it.isNotEmpty() }.joinToString(" · "))
            }
        }

        if (rx.limits.isNotEmpty()) Bullets("Limits", rx.limits, warning = true)

        if (rx.adjustmentRules.isNotEmpty()) {
            Heading("Adjusting the next session")
            rx.adjustmentRules.forEach { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            (if (r.label.isNotEmpty()) "${r.label}. " else "") + "If " + r.condition,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(r.action)
                    }
                }
            }
        }

        if (rx.trackingInstructions.isNotEmpty()) Bullets("What to record", rx.trackingInstructions)

        Heading("Exercises")
        if (rx.exercises.isEmpty()) Text("None found on the sheet")
        rx.exercises.forEach { ExerciseCard(it) }

        if (rx.generalPrecautions.isNotEmpty()) Bullets("Precautions", rx.generalPrecautions, warning = true)
        if (rx.otherNotes.isNotEmpty()) Bullets("Other notes", rx.otherNotes)
    }
}

@Composable
private fun ExerciseCard(ex: Exercise) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ex.name, style = MaterialTheme.typography.titleSmall)
            Field("Setup", ex.setup)
            ex.steps.forEachIndexed { i, s -> Text("${i + 1}. $s") }
            Field("Pace", ex.pace)
            Field("Sets", ex.sets)
            Field("Reps", ex.reps)
            Field("Hold / duration", ex.holdOrDuration)
            Field("Frequency", ex.frequency)
            Field("Intensity / load", ex.intensityOrLoad)
            if (ex.precautions.isNotEmpty()) Bullets("Precautions", ex.precautions, warning = true)
        }
    }
}

@Composable
private fun Heading(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
}

/** "Label: value", or nothing when the sheet didn't include it. */
@Composable
private fun Field(label: String, value: String) {
    if (value.isNotEmpty()) Text("$label: $value")
}

@Composable
private fun Bullets(title: String, items: List<String>, warning: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        items.forEach { Text("• $it") }
    }
}