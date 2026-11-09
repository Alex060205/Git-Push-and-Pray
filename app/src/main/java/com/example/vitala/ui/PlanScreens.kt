package com.example.vitala.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vitala.Recognition.PrescriptionViewModel
import com.example.vitala.Recognition.ScanUiState
import com.example.vitala.Recognition.loadScaledBitmap
import com.example.vitala.app.*
import com.example.vitala.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter

// ------------------------------------------------------------------ Plan tab

@Composable
fun PlanScreen(s: AppState, vm: AppViewModel, bar: @Composable () -> Unit) {
    val conditions = s.activeConditions
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val cond = conditions.firstOrNull { it.id == selectedId } ?: conditions.firstOrNull()
    val plan = cond?.let { s.currentPlan(it.id) }
    val program = plan?.let { s.programs[it.id] }
    val rx = program?.source

    Page(
        title = "My plan", bottomBar = bar,
        actions = {
            if (cond != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (plan == null) "Add plan" else "Update plan", { vm.go(if (plan == null) Screen.AddPlan(cond.id) else Screen.UpdatePlan(cond.id)) }, Modifier.weight(1f))
                if (plan != null) SecondaryButton("Plan history", { vm.go(Screen.PlanHistory(cond.id)) }, Modifier.weight(1f))
            }
        }
    ) {
        // PT-1: tabs per active condition + add
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            conditions.forEach { c -> ChoiceChip(c.id == cond?.id, c.name, { selectedId = c.id }) }
            Row(
                Modifier.height(56.dp).clip(RoundedCornerShape(4.dp)).border(2.dp, actionColor(), RoundedCornerShape(4.dp))
                    .clickable { vm.go(Screen.AddCondition(false)) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { Icon(Icons.Outlined.Add, null, tint = actionColor()); Spacer(Modifier.width(4.dp)); Text("Add condition", color = actionColor(), style = MaterialTheme.typography.labelLarge) }
        }
        if (cond == null) {
            Panel { Text("No conditions yet", style = MaterialTheme.typography.titleSmall); Hint("Add a condition, then your clinician's plan.") }
            return@Page
        }
        if (plan == null) {
            Panel {
                Text(cond.name, style = MaterialTheme.typography.titleMedium)
                Text("No plan added yet. You can add it by photo, from a file, or by typing it in.")
            }
            return@Page
        }
        Panel {
            Text(cond.name, style = MaterialTheme.typography.titleMedium)
            Hint(listOf(cond.diagnosis, plan.doctor, if (plan.planDate.isNotBlank()) "Plan date ${plan.planDate}" else "", "Version ${plan.version}").filter { it.isNotBlank() }.joinToString(" · "))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (plan.incomplete) "Incomplete" else "Complete", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = if (plan.incomplete) AmberText else GreenDark,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (plan.incomplete) AmberTint else GreenTint).padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        val missing = plan.items.filter { it.status == ItemStatus.MISSING.name }
        if (missing.isNotEmpty()) AmberNotice(
            text = "Plan incomplete. ${missing.size} ${if (missing.size == 1) "item is" else "items are"} missing: ask your clinician about " +
                    missing.joinToString(" and ") { "“${it.label}”" } + ". Until then your exercise time is held at the plan's amount and nothing is made up."
        )
        plan.items.filter { it.key.startsWith("exercise_") }.forEach { ex ->
            Panel {
                Text(ex.label, style = MaterialTheme.typography.titleMedium)
                Text(ex.summary, style = MaterialTheme.typography.bodyLarge)
                val t = vm.todayItems().firstOrNull { it.plan.id == plan.id }
                if (t != null) Hint("Day ${t.programDay.coerceAtLeast(1)} of ${t.totalDays}" + (rx?.reviewPlan?.takeIf { it.isNotBlank() }?.let { " · review: $it" } ?: ""))
                SecondaryButton("See how to do it", { vm.go(Screen.PlanItemDetail(plan.id, ex.key)) })
            }
        }
        plan.items.filter { !it.key.startsWith("exercise_") }.forEach { item ->
            ListRow(item.label, if (item.status == ItemStatus.MISSING.name) "Ask your clinician about this" else item.summary,
                onClick = { vm.go(Screen.PlanItemDetail(plan.id, item.key)) }, trailing = { if (item.status != ItemStatus.OK.name) StatusPill(item.status) })
        }
        if (plan.items.any { it.key == "clinic" && it.status == ItemStatus.MISSING.name }) SafetyPanel(Safety.GENERIC)   // PL-12
        rx?.clinicContact?.takeIf { it.isNotBlank() }?.let { Hint("Clinic: $it") }
    }
}

@Composable
fun PlanItemDetailScreen(s: AppState, vm: AppViewModel, programId: String, key: String) {
    val item = s.plans.firstOrNull { it.id == programId }?.items?.firstOrNull { it.key == key }
    Page(title = item?.label ?: "Plan", onBack = vm::back) {
        if (item == null) { Text("Not found."); return@Page }
        StatusPill(item.status)
        if (item.status == ItemStatus.MISSING.name) {
            Text("Your plan doesn't say this. Ask your clinician; the app won't guess it.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(item.summary, style = MaterialTheme.typography.titleSmall)
            if (item.detail.isNotBlank()) Text(item.detail.trim(), style = MaterialTheme.typography.bodyLarge)
        }
        if (item.key == "clinic") {
            val rx = s.programs[programId]?.source
            rx?.clinicContact?.takeIf { it.isNotBlank() }?.let { Panel { Text("Clinic contact", style = MaterialTheme.typography.titleSmall); Text(it) } }
            SafetyPanel(Safety.GENERIC)
        }
    }
}

// ------------------------------------------------------------------ capture

@Composable
fun AddPlanScreen(s: AppState, vm: AppViewModel, conditionId: String, onboarding: Boolean, update: Boolean) {
    val scan: PrescriptionViewModel = viewModel()
    val st by scan.state.collectAsState()
    val cond = s.conditions.firstOrNull { it.id == conditionId }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> scan.onPhotoResult(ok) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ScanUiState.MAX_PAGES)) { scan.onImagesPicked(it) }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { scan.onDocumentPicked(it) }

    LaunchedEffect(Unit) { scan.clearPages() }
    // Once the plan is read and prepared, the person must pass through the review screen (PL-2).
    LaunchedEffect(st.preparedProgram) {
        val now = scan.state.value                       // fresh read: the clear above may have just run
        if (now.preparedProgram != null) vm.go(Screen.PlanReviewScreen(conditionId, if (now.sourceText != null) "manual" else if (now.preview != null) "file" else "photo", onboarding))
    }

    Page(
        title = if (update) "Update plan" else "Add the plan", subtitle = cond?.name?.let { if (update) "$it · add the new plan from your clinician" else it },
        onBack = { if (onboarding) vm.setOnboardingStep(2, Screen.AddCondition(true)) else vm.back() },
        steps = if (!update) 2 to 2 else null, topRight = if (!update) "Step 2 of 2" else null,
        actions = {
            if (st.pages.isNotEmpty()) PrimaryButton(if (st.pages.size == 1) "Read my plan" else "Read ${st.pages.size} pages", scan::analyzePages, enabled = !st.busy)
            if (onboarding || !update) LinkButton("I'll do this later", {
                if (onboarding) vm.setOnboardingStep(5, Screen.OnboardMedicines) else vm.back()
            })
        }
    ) {
        BigOption(Icons.Outlined.PhotoCamera, "Take a photo", "Photograph each page of your plan.", enabled = st.canAddPages) {
            try { takePicture.launch(scan.createPhotoUri()) } catch (e: ActivityNotFoundException) { scan.onCameraUnavailable() }
        }
        BigOption(Icons.Outlined.Image, "Choose a photo", "Pick up to ${com.example.vitala.Recognition.ScanUiState.MAX_PAGES} pages from your photos.", enabled = st.canAddPages) {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        BigOption(Icons.Outlined.Edit, "Type it in", "Enter your plan field by field.", enabled = !st.busy) { vm.go(Screen.ManualPlan(conditionId, onboarding)) }
        LinkButton("Choose a PDF or text file", { pickDoc.launch(arrayOf("application/pdf", "text/plain")) }, Modifier.align(Alignment.CenterHorizontally))

        if (st.pages.isNotEmpty()) {
            Text("Pages (${st.pages.size})", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(st.pages, key = { _, p -> p.path }) { i, page ->
                    Box {
                        Image(page.thumbnail.asImageBitmap(), "Page ${i + 1}", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 96.dp, height = 128.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, borderColor(), RoundedCornerShape(4.dp)))
                        Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(actionColor(), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp))
                    }
                }
            }
            LinkButton("Remove all pages", scan::clearPages)
        }
        Busy(st.busy, if (st.isAnalyzing) "Reading your plan. This can take a minute…" else "Working…")
        st.error?.let { ErrorText(it) }
        Panel { Text("You'll check each item before it's saved. Nothing changes without your OK.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun BigOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightInMin().clip(RoundedCornerShape(4.dp)).background(Color.White).border(2.dp, if (enabled) actionColor() else borderColor(), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(Panel), contentAlignment = Alignment.Center) { Icon(icon, null, tint = actionColor(), modifier = Modifier.size(30.dp)) }
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Hint(sub) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = actionColor(), modifier = Modifier.size(30.dp))
    }
}

private fun Modifier.heightInMin() = this.then(Modifier.height(88.dp))

// ------------------------------------------------------------------ typed form

private data class FormField(val key: String, val label: String, val example: String, val multiline: Boolean = false, val required: Boolean = false)

private val FORM = listOf(
    FormField("exercise", "Exercise name", "Gentle tendon glides", required = true),
    FormField("how", "How to do it", "Hand upright, hook, loose fist, straight fist, return…", multiline = true),
    FormField("dose", "How much", "Up to 2 minutes, with short rests"),
    FormField("intensity", "How hard (intensity or resistance)", "Slow and comfortable"),
    FormField("frequency", "How often", "Once a day"),
    FormField("increase", "How to increase", "Add 1 minute, at most once a week, never above 4 minutes", multiline = true),
    FormField("evidence", "How many steady sessions before increasing", "2 sessions in a row with no rise in pain"),
    FormField("feel", "What is OK to feel", "Familiar discomfort that settles", multiline = true),
    FormField("missed", "If you miss or shorten a session", "Do not make it up the next day", multiline = true),
    FormField("backup", "Backup plan", "1 minute less if pain is up 2 points or more", multiline = true),
    FormField("limits", "Fatigue and daily limits", "Fatigue should not get much worse"),
    FormField("clinic", "When to stop or call your clinic", "New numbness, tingling or weakness", multiline = true)
)

@Composable
fun ManualPlanScreen(s: AppState, vm: AppViewModel, conditionId: String, onboarding: Boolean) {
    val scan: PrescriptionViewModel = viewModel()
    val st by scan.state.collectAsState()
    val values = remember { mutableStateOf(mapOf<String, String>()) }
    val notOnPlan = remember { mutableStateOf(setOf<String>()) }
    var doctor by remember { mutableStateOf(s.profile.medicalProvider) }
    var contact by remember { mutableStateOf("") }
    var planDate by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { scan.clearPages() }
    LaunchedEffect(st.preparedProgram) {
        if (scan.state.value.preparedProgram != null) vm.go(Screen.PlanReviewScreen(conditionId, "manual", onboarding))
    }
    val canRead = !values.value["exercise"].isNullOrBlank() && !st.busy
    Page(
        title = "Type in your plan", subtitle = "Copy what your plan says into each field.",
        onBack = vm::back,
        actions = {
            PrimaryButton("Check my plan", {
                val text = buildString {
                    appendLine("EXERCISE PRESCRIPTION (typed in by the patient from their clinician's plan)")
                    if (doctor.isNotBlank()) appendLine("Prescriber: $doctor")
                    if (contact.isNotBlank()) appendLine("Clinic contact: $contact")
                    if (planDate.isNotBlank()) appendLine("Date approved: $planDate")
                    if (start.isNotBlank()) appendLine("Start date: $start (day 1)")
                    FORM.forEach { f ->
                        if (f.key in notOnPlan.value) appendLine("${f.label}: NOT ON THE PLAN (leave this out, do not guess)")
                        else values.value[f.key]?.takeIf { it.isNotBlank() }?.let { appendLine("${f.label}: $it") }
                    }
                }
                scan.analyzeTypedPlan(text)
            }, enabled = canRead)
        }
    ) {
        Hint("If your plan doesn't include a field, tick \"Not on my plan\".")
        LabeledField("Prescribing doctor", doctor, { doctor = it }, helper = "Filled in from your details. Check it's correct.")
        LabeledField("Clinic phone", contact, { contact = it }, keyboard = androidx.compose.ui.text.input.KeyboardType.Phone)
        LabeledField("Date approved", planDate, { planDate = it }, placeholder = "For example: Nov 8, 2026")
        LabeledField("Start date", start, { start = it }, placeholder = "For example: Nov 9, 2026")
        FORM.forEach { f ->
            val off = f.key in notOnPlan.value
            LabeledField(f.label + if (f.required) " (required)" else "", if (off) "" else values.value[f.key].orEmpty(),
                { v -> values.value = values.value + (f.key to v) }, placeholder = "e.g. ${f.example}", singleLine = !f.multiline, minLines = if (f.multiline) 2 else 1,
                modifier = Modifier.then(if (off) Modifier.background(Panel) else Modifier))
            if (!f.required) CheckCard(off, "Not on my plan", { on -> notOnPlan.value = if (on) notOnPlan.value + f.key else notOnPlan.value - f.key }, "My plan doesn't include this, so leave it blank.")
        }
        Busy(st.busy, "Reading your plan…")
        st.error?.let { ErrorText(it) }
    }
}

// ------------------------------------------------------------------ review

@Composable
fun PlanReviewScreen(s: AppState, vm: AppViewModel, conditionId: String, source: String, onboarding: Boolean) {
    val scan: PrescriptionViewModel = viewModel()
    val st by scan.state.collectAsState()
    val rx = st.prescription
    val prepared = st.preparedProgram
    if (rx == null || prepared == null) {
        Page(title = "Check your plan", onBack = vm::back) { Text("Nothing to review yet. Go back and add your plan.") }
        return
    }
    val items = remember(prepared) { PlanReview.build(rx, prepared) }
    var doctor by remember { mutableStateOf(rx.prescriber.ifBlank { s.profile.medicalProvider }) }
    var confirmed by remember { mutableStateOf(setOf<String>()) }
    var open by remember { mutableStateOf<String?>(null) }
    val needsConfirm = items.filter { it.status == ItemStatus.CHECK.name }
    val allConfirmed = needsConfirm.all { it.key in confirmed }
    val missing = items.filter { it.status == ItemStatus.MISSING.name }

    Page(
        title = "Check your plan", subtitle = when (source) { "manual" -> "Read from what you typed. Check each line against your plan."; else -> "Read from your ${if (source == "file") "file" else "photo"}. Check each line against your plan." },
        onBack = vm::back,
        actions = {
            PrimaryButton("Save plan", {
                vm.savePlan(conditionId, rx, prepared, source, doctor, st.pages.map { it.path }, onboarding) { scan.finishScan() }
            }, enabled = allConfirmed && !s.busy)
            LinkButton(if (source == "manual") "Edit what I typed" else "Retake photo", vm::back)
        }
    ) {
        items.forEach { item ->
            val expanded = open == item.key
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, borderColor(), RoundedCornerShape(4.dp))
                    .clickable { open = if (expanded) null else item.key }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.titleSmall)
                        Text(if (item.status == ItemStatus.MISSING.name) "Not found on your plan" else item.summary, style = MaterialTheme.typography.bodySmall, color = secondaryText())
                    }
                    StatusPill(item.status)
                }
                if (expanded && item.detail.isNotBlank()) Text(item.detail.trim(), style = MaterialTheme.typography.bodySmall)
            }
            if (item.status == ItemStatus.CHECK.name) {
                // PL-5: wording that the engine needs as a limit is shown in plain words and confirmed, or it stays unconfirmed.
                CheckCard(item.key in confirmed, "Matches my plan", { on -> confirmed = if (on) confirmed + item.key else confirmed - item.key }, "“${item.summary}” is what my plan says.")
            }
        }
        if (rx.unreadable.isNotEmpty()) AmberNotice("Could not read clearly", rx.unreadable.joinToString("; "))
        LabeledField("Prescribing doctor", doctor, { doctor = it }, helper = "Check this matches your plan.")
        if (st.pages.isNotEmpty()) {
            Text("Your original", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                st.pages.forEachIndexed { i, p ->
                    Image(p.thumbnail.asImageBitmap(), "Page ${i + 1}", contentScale = ContentScale.Crop, modifier = Modifier.size(width = 90.dp, height = 120.dp).clip(RoundedCornerShape(4.dp)))
                }
            }
        }
        if (missing.isNotEmpty()) Hint("Missing items stay on your list.")
        if (missing.isNotEmpty()) Hint("Your session length won't change until you've asked your clinician about them.")
        Busy(s.busy, "Saving your plan…")
        ErrorText(s.error)
    }
}

// ------------------------------------------------------------------ reminder times (PL-13)

@Composable
fun ReminderTimesScreen(s: AppState, vm: AppViewModel, onboarding: Boolean, fromSave: Boolean) {
    val items = vm.todayItems()
    val base = remember(s.settings.exerciseTimes, items.size) {
        items.map { it.exerciseId to it }.map { (id, item) ->
            s.settings.exerciseTimes.firstOrNull { it.exerciseId == id }
                ?: ExerciseTime(item.name, id, s.settings.usualExerciseTime, s.settings.leadMinutes)
        }
    }
    var edited by remember(base) { mutableStateOf(base) }
    var timeDialog by remember { mutableStateOf<String?>(null) }
    var leadDialog by remember { mutableStateOf<String?>(null) }
    fun finish() {
        if (onboarding) vm.setOnboardingStep(5, Screen.OnboardMedicines)
        else if (fromSave) vm.resetTo(Screen.TabRoot(Tab.PLAN))
        else vm.back()
    }
    Page(
        title = "Exercise times", subtitle = "Pick when you'll do each exercise. You'll get a reminder before it.",
        onBack = if (fromSave) null else vm::back,
        topRight = if (fromSave) "Plan saved" else null,
        actions = {
            PrimaryButton("Save times", {
                val others = s.settings.exerciseTimes.filter { e -> edited.none { it.exerciseId == e.exerciseId } }
                vm.saveSettings { copy(exerciseTimes = others + edited) }
                finish()
            })
            if (fromSave) LinkButton("Skip for now", ::finish)
        }
    ) {
        if (fromSave) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, null, tint = GreenDark); Spacer(Modifier.width(6.dp)); Text("Plan saved", color = GreenDark, style = MaterialTheme.typography.titleSmall)
        }
        if (items.isEmpty()) Text("No exercises to remind you about yet.")
        edited.forEach { et ->
            val item = items.firstOrNull { it.exerciseId == et.exerciseId }
            Panel {
                Text(et.exerciseName, style = MaterialTheme.typography.titleMedium)
                val freq = item?.let { s.programs[it.plan.id]?.source?.exercises?.getOrNull(it.compiled.sourceIndex)?.frequency }.orEmpty()
                Hint(listOfNotNull(freq.ifBlank { null }, item?.upTo?.let { "up to ${Dose.fmt(item.dimension, it)}" }).joinToString(" · "))
                Question("Exercise time", "When you'll do this exercise.")
                val quick = listOf("09:00", s.settings.usualExerciseTime, "14:00").distinct()
                ChipRow { quick.forEach { t -> ChoiceChip(et.time == t, fmtClock(t), { edited = edited.map { if (it.exerciseId == et.exerciseId) it.copy(time = t) else it } }, Modifier.weight(1f)) } }
                LinkButton("Choose another time", { timeDialog = et.exerciseId })
                Question("Reminder", "How early you'll get a reminder.")
                ListRow("${et.leadMinutes} minutes before", onClick = { leadDialog = et.exerciseId })
            }
        }
        Hint("Change these any time in Settings > Notifications.")
        Hint("To get suggested times when you're free, link your calendar in Settings > Calendar.")
        timeDialog?.let { id ->
            TimeDialog(edited.first { it.exerciseId == id }.time, { timeDialog = null }) { t -> edited = edited.map { if (it.exerciseId == id) it.copy(time = t) else it }; timeDialog = null }
        }
        leadDialog?.let { id ->
            ChoiceDialog("Remind me", LEAD_OPTIONS, edited.first { it.exerciseId == id }.leadMinutes.toString(),
                { v -> edited = edited.map { if (it.exerciseId == id) it.copy(leadMinutes = v.toInt()) else it }; leadDialog = null }, { leadDialog = null }, LEAD_DESCRIPTIONS)
        }
    }
}

// ------------------------------------------------------------------ history

@Composable
fun PlanHistoryScreen(s: AppState, vm: AppViewModel, conditionId: String) {
    val cond = s.conditions.firstOrNull { it.id == conditionId }
    val versions = s.plans.filter { it.conditionId == conditionId }.sortedByDescending { it.version }
    Page(title = "Plan history", subtitle = cond?.name, onBack = vm::back) {
        versions.forEach { p ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.White).border(if (p.current) 3.dp else 1.dp, if (p.current) actionColor() else borderColor(), RoundedCornerShape(4.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Version ${p.version}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (p.current) Text("Current", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(RoundedCornerShape(50)).background(actionColor()).padding(horizontal = 12.dp, vertical = 2.dp))
                }
                Hint(listOf(if (p.planDate.isNotBlank()) "Approved ${p.planDate}" else "Saved ${p.savedOn}", if (p.startDate.isNotBlank()) "starts ${p.startDate}" else "", p.doctor, p.replacedOn?.let { "replaced $it" } ?: "").filter { it.isNotBlank() }.joinToString(" · "))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("View plan", { vm.go(Screen.PlanVersionView(p.id)) }, Modifier.weight(1f))
                    SecondaryButton("View photo", { vm.go(Screen.PlanPhotos(p.id)) }, Modifier.weight(1f), enabled = p.imagePaths.isNotEmpty())
                }
            }
        }
        if (versions.size <= 1) Hint("No earlier plans yet. When your clinician updates your plan, the old one stays here so your history stays correct.")
    }
}

@Composable
fun PlanVersionViewScreen(s: AppState, vm: AppViewModel, programId: String) {
    val p = s.plans.firstOrNull { it.id == programId }
    Page(title = "Version ${p?.version ?: ""}", subtitle = "View only", onBack = vm::back) {
        if (p == null) { Text("Not found."); return@Page }
        Hint(listOf(p.doctor, if (p.planDate.isNotBlank()) "Approved ${p.planDate}" else "").filter { it.isNotBlank() }.joinToString(" · "))
        p.items.forEach { item ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(item.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); StatusPill(item.status) }
                Text(if (item.status == ItemStatus.MISSING.name) "Not found on this plan" else item.summary)
                if (item.detail.isNotBlank() && item.status != ItemStatus.MISSING.name) Hint(item.detail.trim())
            }
        }
    }
}

@Composable
fun PlanPhotosScreen(s: AppState, vm: AppViewModel, programId: String) {
    val p = s.plans.firstOrNull { it.id == programId }
    Page(title = "Your original plan", subtitle = "Version ${p?.version ?: ""}", onBack = vm::back) {
        p?.imagePaths.orEmpty().forEachIndexed { i, path ->
            val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
                value = withContext(Dispatchers.IO) { runCatching { loadScaledBitmap(File(path), 1600).asImageBitmap() }.getOrNull() }
            }
            bmp?.let { Image(it, "Page ${i + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().border(1.dp, borderColor())) }
                ?: Hint("Loading page ${i + 1}…")
        }
        if (p?.imagePaths.isNullOrEmpty()) Text("No photo was saved with this version.")
    }
}
