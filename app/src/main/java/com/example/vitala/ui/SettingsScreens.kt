package com.example.vitala.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vitala.app.*
import com.example.vitala.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(s: AppState, vm: AppViewModel, bar: @Composable () -> Unit) {
    val st = s.settings
    Page(title = "Settings", bottomBar = bar) {
        ListRow("My conditions", s.activeConditions.joinToString(", ") { it.name }.ifBlank { "None yet" }, { vm.go(Screen.Conditions) })
        ListRow("My medicines", s.meds.filter { !it.stopped }.joinToString(", ") { it.name }.ifBlank { "None yet" }, { vm.go(Screen.Medicines) })
        ListRow("Health information", if (s.profile.heightCm == null && s.profile.weightKg == null) "Not added yet" else "Added", { vm.go(Screen.Health) })
        ListRow("Notifications", "Reminders and times", { vm.go(Screen.Notifications) })
        ListRow("Calendar", if (st.calendarLinked) "Linked: ${st.calendarSource}" else "Not linked", { vm.go(Screen.Calendar) })
        ListRow("Text size and display", st.textSizeChoice.label + if (st.highContrast) " · high contrast" else "", { vm.go(Screen.TextSize) })
        ListRow("Account", s.profile.fullName.ifBlank { s.email }, { vm.go(Screen.Account) })
        Hint("This app supports your clinician's plan. It is not a diagnosis or treatment tool.")
    }
}

// ------------------------------------------------------------------ conditions

@Composable
fun ConditionsScreen(s: AppState, vm: AppViewModel) {
    Page(title = "My conditions", onBack = vm::back, actions = { PrimaryButton("Add a condition", { vm.go(Screen.AddCondition(false)) }) }) {
        s.activeConditions.forEach { c -> ListRow(c.name, c.diagnosis.ifBlank { null }, { vm.go(Screen.EditCondition(c.id)) }) }
        if (s.activeConditions.isEmpty()) Text("No conditions yet.")
        val archived = s.conditions.filter { it.archived }
        if (archived.isNotEmpty()) {
            SectionLabel("Archived")
            archived.forEach { c -> ListRow(c.name, "Archived · history kept", { vm.go(Screen.EditCondition(c.id)) }) }
        }
    }
}

@Composable
fun EditConditionScreen(s: AppState, vm: AppViewModel, id: String) {
    val c = s.conditions.firstOrNull { it.id == id }
    if (c == null) { Page(title = "Condition", onBack = vm::back) { Text("Not found.") }; return }
    var name by remember { mutableStateOf(c.name) }
    var dx by remember { mutableStateOf(c.diagnosis) }
    var confirm by remember { mutableStateOf(false) }
    Page(
        title = "Edit condition", onBack = vm::back,
        actions = {
            PrimaryButton("Save", { vm.updateCondition(c.copy(name = name.trim(), diagnosis = dx.trim())); vm.back() }, enabled = name.isNotBlank())
            if (c.archived) SecondaryButton("Restore this condition", { vm.updateCondition(c.copy(archived = false)); vm.back() })
            else DestructiveButton("Archive this condition", { confirm = true })
        }
    ) {
        LabeledField("What should we call it?", name, { name = it })
        LabeledField("Diagnosis from your doctor", dx, { dx = it })
        Hint("Archiving hides the condition and stops its reminders. Its plans and history are kept.")
        if (confirm) ConfirmDialog("Archive this condition?", "Its exercises will leave Home and reminders stop. Your history is kept and you can restore it.", "Archive",
            { vm.updateCondition(c.copy(archived = true)); confirm = false; vm.back() }, { confirm = false }, destructive = true)
    }
}

// ------------------------------------------------------------------ medicines

@Composable
fun MedicinesScreen(s: AppState, vm: AppViewModel) {
    Page(title = "My medicines", onBack = vm::back, actions = { PrimaryButton("Add a medicine", { vm.go(Screen.EditMedicine(null)) }) }) {
        val active = s.meds.filter { !it.stopped }
        active.forEach { m ->
            ListRow(m.name, listOf(m.dose, if (m.asNeeded) "only when needed" else m.times.sorted().joinToString(", ") { fmtClock(it) }).filter { it.isNotBlank() }.joinToString(" · "), { vm.go(Screen.EditMedicine(m.id)) })
        }
        if (active.isEmpty()) Text("None yet.")
        val stopped = s.meds.filter { it.stopped }
        if (stopped.isNotEmpty()) { SectionLabel("Stopped"); stopped.forEach { ListRow(it.name, "Stopped${it.endDate?.let { d -> " $d" } ?: ""} · history kept", { vm.go(Screen.EditMedicine(it.id)) }) } }
        Hint("The app never gives advice about doses, mixing medicines or changes to medicines.")
    }
}

@Composable
fun EditMedicineScreen(s: AppState, vm: AppViewModel, id: String?) {
    val existing = s.meds.firstOrNull { it.id == id }
    var m by remember { mutableStateOf(existing ?: MedicationRec()) }
    var timeDialog by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Page(
        title = if (existing == null) "Add a medicine" else "Edit medicine", onBack = vm::back,
        actions = {
            PrimaryButton("Save medicine", { vm.saveMedication(m.copy(name = m.name.trim(), dose = m.dose.trim())); vm.back() }, enabled = m.name.isNotBlank() && (m.asNeeded || m.times.isNotEmpty()))
            if (existing != null && !existing.stopped) DestructiveButton("Stop this medicine", { confirm = true })
        }
    ) {
        LabeledField("Name", m.name, { m = m.copy(name = it) }, placeholder = "Medicine name")
        LabeledField("How much", m.dose, { m = m.copy(dose = it) }, placeholder = "For example: 1 tablet")
        if (!m.asNeeded) {
            Question("Times", "Add each time of day you take it.")
            val times = m.times.sorted()
            times.chunked(2).forEach { row -> ChipRow { row.forEach { t -> ChoiceChip(true, fmtClock(t), { m = m.copy(times = m.times - t) }, Modifier.weight(1f)) }; if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) } }
            SecondaryButton("+ Add a time", { timeDialog = true })
            Hint("Tap a time to remove it.")
        }
        ToggleRow("Take with food", m.withFood, { m = m.copy(withFood = it) })
        ToggleRow("Only when needed", m.asNeeded, { m = m.copy(asNeeded = it) }, "No set times, so no reminders.")
        if (!m.asNeeded) {
            ToggleRow("Remind me", m.remindersOn, { m = m.copy(remindersOn = it) })
            if (m.remindersOn) ToggleRow("Important", m.important, { m = m.copy(important = it) }, "Reminds you even during quiet hours.")
        }
        if (timeDialog) TimeDialog("08:00", { timeDialog = false }) { t -> m = m.copy(times = (m.times + t).distinct()); timeDialog = false }
        if (confirm) ConfirmDialog("Stop this medicine?", "Reminders stop. Your history is kept.", "Stop it", { vm.stopMedication(m.id); confirm = false; vm.back() }, { confirm = false }, destructive = true)
    }
}

@Composable
fun OnboardMedicinesScreen(s: AppState, vm: AppViewModel) {
    Page(
        title = "Medicines", subtitle = "Optional. Add the medicines you take to get reminders.", onBack = { vm.setOnboardingStep(4.coerceAtMost(if (s.plans.any { it.current }) 4 else 2)) },
        actions = {
            PrimaryButton(if (s.meds.any { !it.stopped }) "Next" else "Skip for now", { vm.setOnboardingStep(6, Screen.OnboardCalendar) })
            if (s.meds.none { !it.stopped }) SecondaryButton("Add a medicine", { vm.go(Screen.EditMedicine(null)) })
        }
    ) {
        s.meds.filter { !it.stopped }.forEach { Panel { Text(it.name, style = MaterialTheme.typography.titleSmall); Hint(it.dose) } }
        if (s.meds.any { !it.stopped }) SecondaryButton("Add another", { vm.go(Screen.EditMedicine(null)) })
        Hint("You can add or change medicines any time in Settings.")
    }
}

// ------------------------------------------------------------------ health information

@Composable
fun HealthScreen(s: AppState, vm: AppViewModel) {
    var dialog by remember { mutableStateOf<String?>(null) }
    Page(
        title = "Health information", subtitle = if (s.profile.heightCm == null && s.profile.weightKg == null) "Not added yet" else "Last updated ${s.profile.healthUpdated ?: ""}", onBack = vm::back
    ) {
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Hint("Height"); Text(s.profile.heightCm?.let { "${it.toInt()} cm" } ?: "Not added", style = MaterialTheme.typography.titleSmall) }
                SecondaryButton(if (s.profile.heightCm == null) "Add" else "Change", { dialog = "h" }, Modifier.width(130.dp))
            }
        }
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Hint("Weight"); Text(s.profile.weightKg?.let { "%.1f kg".format(it) } ?: "Not added", style = MaterialTheme.typography.titleSmall) }
                SecondaryButton(if (s.profile.weightKg == null) "Add" else "Change", { dialog = "w" }, Modifier.width(130.dp))
            }
        }
        Hint("Optional. Included in the PDF report for your clinician.")
        Hint("Update them when they change.")
        when (dialog) {
            "h" -> NumberDialog("Height", "cm", s.profile.heightCm?.toInt()?.toString().orEmpty(), { vm.saveHealth(it, null); dialog = null }, { dialog = null })
            "w" -> NumberDialog("Weight", "kg", s.profile.weightKg?.toString().orEmpty(), { vm.saveHealth(null, it); dialog = null }, { dialog = null })
        }
    }
}

// ------------------------------------------------------------------ notifications

@Composable
fun NotificationsScreen(s: AppState, vm: AppViewModel) {
    val st = s.settings
    var dialog by remember { mutableStateOf<String?>(null) }
    Page(title = "Notifications", subtitle = "Choose which reminders you get and when.", onBack = vm::back) {
        NotificationStatus(vm)

        SectionLabel("Exercise")
        ToggleRow("Exercise reminders", st.exerciseRemindersOn, { v -> vm.saveSettings { copy(exerciseRemindersOn = v) } },
            if (st.exerciseRemindersOn) "${st.leadMinutes} minutes before each session" else "Off")
        if (st.exerciseRemindersOn) {
            ListRow("Exercise times", st.exerciseTimes.joinToString(", ") { "${it.exerciseName} · ${fmtClock(it.time)}" }.ifBlank { "Set after you save a plan" },
                { vm.go(Screen.ReminderTimes(false, false)) })
            ListRow("How early to remind me", "${st.leadMinutes} minutes before", { dialog = "lead" })
        }

        SectionLabel("Medicines")
        ToggleRow("Medicine reminders", st.medRemindersOn, { v -> vm.saveSettings { copy(medRemindersOn = v) } },
            if (st.medRemindersOn) "At the times set for each medicine" else "Off")
        if (st.medRemindersOn) {
            ToggleRow("Show medicine names on the lock screen", st.showMedNames, { v -> vm.saveSettings { copy(showMedNames = v) } },
                "When off, the notification says \"Time for your medicine\".")
            val meds = s.meds.filter { !it.stopped }
            ListRow("My medicines and times", meds.joinToString(", ") { it.name }.ifBlank { "None added yet" }, { vm.go(Screen.Medicines) })
        }

        SectionLabel("Check-ins")
        ToggleRow("Daily check-in", st.checkInRemindersOn, { v -> vm.saveSettings { copy(checkInRemindersOn = v) } },
            if (st.checkInRemindersOn) "Every day at ${fmtClock(st.checkInTime)}" else "Off")
        if (st.checkInRemindersOn) ListRow("Daily check-in time", fmtClock(st.checkInTime), { dialog = "daily" })
        ToggleRow("Weekly check-in", st.weeklyOn, { v -> vm.saveSettings { copy(weeklyOn = v) } },
            if (st.weeklyOn) "${DayOfWeek.of(st.weeklyDay).getDisplayName(TextStyle.FULL, Locale.getDefault())}s at ${fmtClock(st.weeklyTime)}" else "Off")
        if (st.weeklyOn) ListRow("Weekly check-in day and time",
            "${DayOfWeek.of(st.weeklyDay).getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${fmtClock(st.weeklyTime)}", { dialog = "weekly" })

        SectionLabel("Quiet hours")
        ToggleRow("Quiet hours", st.quietOn, { v -> vm.saveSettings { copy(quietOn = v) } },
            if (st.quietOn) "No reminders from ${fmtClock(st.quietStart)} to ${fmtClock(st.quietEnd)}" else "Off")
        if (st.quietOn) {
            ListRow("Quiet hours times", "${fmtClock(st.quietStart)} to ${fmtClock(st.quietEnd)}", { dialog = "quiet" })
            Hint("Medicines you mark as important can still remind you during quiet hours.")
        }

        when (dialog) {
            "lead" -> ChoiceDialog("Remind me", LEAD_OPTIONS, st.leadMinutes.toString(), { v ->
                vm.saveSettings { copy(leadMinutes = v.toInt(), exerciseTimes = exerciseTimes.map { it.copy(leadMinutes = v.toInt()) }) }; dialog = null }, { dialog = null }, LEAD_DESCRIPTIONS)
            "daily" -> TimeDialog(st.checkInTime, { dialog = null }, { t -> vm.saveSettings { copy(checkInTime = t) }; dialog = null })
            "weekly" -> WeeklyDialog(st.weeklyDay, st.weeklyTime, { d, t -> vm.saveSettings { copy(weeklyDay = d, weeklyTime = t) }; dialog = null }, { dialog = null })
            "quiet" -> QuietDialog(st.quietStart, st.quietEnd, { a, b -> vm.saveSettings { copy(quietStart = a, quietEnd = b) }; dialog = null }, { dialog = null })
        }
    }
}

/**
 * Shows whether reminders can reach the person, with a one-tap fix for each problem,
 * and buttons to send a test. Re-checked every time the screen comes back from system settings.
 */
@Composable
private fun NotificationStatus(vm: AppViewModel) {
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(Reminders.notificationsAllowed(ctx)) }
    var channelsOn by remember { mutableStateOf(Reminders.channelsAllowed(ctx)) }
    var onTime by remember { mutableStateOf(Reminders.canRingOnTime(ctx)) }
    var denied by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        val wasOnTime = onTime
        allowed = Reminders.notificationsAllowed(ctx)
        channelsOn = Reminders.channelsAllowed(ctx)
        onTime = Reminders.canRingOnTime(ctx)
        if (onTime != wasOnTime) vm.rescheduleReminders()   // move existing alarms onto the exact path
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        refresh()
    }
    fun openAppNotificationSettings() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        runCatching { ctx.startActivity(i) }.onFailure {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
        }
    }
    fun turnOn() {
        val canAsk = Build.VERSION.SDK_INT >= 33 && !denied &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (canAsk) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else openAppNotificationSettings()
    }

    when {
        !allowed -> AmberNotice("Notifications are off", "You won't get any reminders until you turn them on.") {
            PrimaryButton(if (denied) "Open phone settings" else "Turn on notifications", ::turnOn, icon = Icons.Outlined.NotificationsActive)
        }
        !channelsOn -> AmberNotice("Some reminders are switched off", "One or more reminder types are turned off in your phone settings.") {
            PrimaryButton("Open phone settings", ::openAppNotificationSettings)
        }
        else -> GreenPanel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = GreenDark)
                Text("Notifications are on", color = GreenDark, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    if (allowed && !onTime && Build.VERSION.SDK_INT >= 31) {
        AmberNotice("Reminders may be late", "Without \"Alarms & reminders\" access, Android can deliver a reminder up to an hour late.") {
            SecondaryButton("Allow on-time reminders", {
                val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", ctx.packageName, null))
                runCatching { ctx.startActivity(i) }
            })
        }
    }

    Panel {
        Text("Test a reminder", style = MaterialTheme.typography.titleSmall)
        Hint("Check that reminders reach you on this phone.")
        SecondaryButton("Send a test now", {
            testMessage = if (Reminders.sendTestNow(ctx)) "Sent. Look at the top of your screen." else { refresh(); "Couldn't send. Turn on notifications first." }
        }, enabled = allowed, icon = Icons.Outlined.NotificationsActive)
        SecondaryButton("Send a test in 10 seconds", {
            Reminders.sendTestLater(ctx, 10)
            testMessage = if (onTime) "Scheduled. Lock your phone; it should arrive in about 10 seconds."
                else "Scheduled. Without on-time access Android may delay it by several minutes."
        }, enabled = allowed, icon = Icons.Outlined.Schedule)
        testMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText()) }
    }
}

// ------------------------------------------------------------------ calendar

@Composable
private fun CalendarLinkOptions(s: AppState, vm: AppViewModel, onLinked: () -> Unit) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val src = pending
        if (granted && src != null) { vm.linkCalendar(src); onLinked() } 
        pending = null
    }
    Text("LINK A CALENDAR", style = MaterialTheme.typography.labelMedium, color = BrandBlueDark)
    listOf("Calendar on this phone", "Google Calendar", "Outlook").forEach { name ->
        ListRow(name, null, { asking = name })
    }
    Hint("Google Calendar and Outlook are read through the calendar on your phone, if you have added that account to your phone.")
    asking?.let { name ->
        ConfirmDialog(
            "Link your calendar?",
            "We only look at when you are busy or free, so we can suggest a better time for your exercise. We do not read or keep event names, people, places or notes. You can unlink at any time.",
            "Continue", { pending = name; asking = null; if (CalendarAvailability.hasPermission(ctx)) { vm.linkCalendar(name); onLinked(); pending = null } else launcher.launch(Manifest.permission.READ_CALENDAR) },
            { asking = null }
        )
    }
}

@Composable
fun CalendarScreen(s: AppState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val st = s.settings
    val canRead = CalendarAvailability.hasPermission(ctx)
    Page(title = "Calendar", onBack = vm::back) {
        Panel {
            Text(if (st.calendarLinked) "Linked: ${st.calendarSource}" else "Not linked", style = MaterialTheme.typography.titleMedium)
            Hint(if (st.calendarLinked) "We use your busy and free times to suggest when to exercise." else "Link your calendar to get suggested times when you are free.")
        }
        if (st.calendarLinked && !canRead) AmberNotice(text = "We can't read your calendar right now (permission was removed), so we're using the times you set.")
        if (st.calendarLinked) {
            ToggleRow("Suggest times for my exercises", st.suggestTimes, { v -> vm.saveSettings { copy(suggestTimes = v) } }, "Based on when your calendar shows you're free.")
            DestructiveButton("Unlink my calendar", vm::unlinkCalendar)
        } else CalendarLinkOptions(s, vm) { }
        Panel {
            Text("Your privacy", style = MaterialTheme.typography.titleSmall)
            Hint("We only see when you are busy or free. We do not read or keep event names. You can unlink at any time.")
        }
        Hint("Your exercises and doses never change because of your calendar.")
    }
}

@Composable
fun OnboardCalendarScreen(s: AppState, vm: AppViewModel) {
    Page(
        title = "Calendar", subtitle = "Optional. Link your calendar to get suggested times when you are free.", onBack = { vm.setOnboardingStep(5, Screen.OnboardMedicines) },
        actions = { PrimaryButton(if (s.settings.calendarLinked) "Finish" else "Skip for now", { vm.setOnboardingStep(7) }) }
    ) {
        if (s.settings.calendarLinked) GreenPanel { Text("Linked: ${s.settings.calendarSource}", color = GreenDark, style = MaterialTheme.typography.titleSmall) }
        else CalendarLinkOptions(s, vm) { }
        Hint("You can link or unlink any time in Settings.")
    }
}

// ------------------------------------------------------------------ display

@Composable
fun TextSizeScreen(s: AppState, vm: AppViewModel) {
    Page(title = "Text size and display", onBack = vm::back) {
        TextSizeChoice.entries.forEach { c ->
            OptionCard(s.settings.textSize == c.name, c.label, { vm.saveSettings { copy(textSize = c.name) } }, c.description)
        }
        ToggleRow("High contrast", s.settings.highContrast, { v -> vm.saveSettings { copy(highContrast = v) } }, "Black text on white, thicker borders")
        Hint("Text also follows your phone's own text size setting.")
    }
}

// ------------------------------------------------------------------ account

@Composable
fun AccountScreen(s: AppState, vm: AppViewModel) {
    val ctx = LocalContext.current
    Page(title = "Account", onBack = vm::back) {
        Panel {
            Text(s.profile.fullName.ifBlank { "Your name" }, style = MaterialTheme.typography.titleMedium)
            Hint(s.email)
            if (s.profile.medicalProvider.isNotBlank()) { Hint("Medical provider"); Text(s.profile.medicalProvider) }
        }
        SecondaryButton("Edit my details", { vm.go(Screen.EditAccount) })
        SecondaryButton("Export my data", { vm.exportMyData { uri ->
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "Export my data"))
        } }, enabled = !s.busy)
        SecondaryButton("Sign out", vm::signOut)
        Busy(s.busy); ErrorText(s.error)
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().androidxClipBg(AlertTint), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Delete my account", style = MaterialTheme.typography.titleSmall, color = Alert)
            Text("This removes your account and all your data. It cannot be undone. We will ask you to confirm.", style = MaterialTheme.typography.bodySmall)
            DestructiveButton("Delete my account", { vm.go(Screen.DeleteAccount) })
        }
    }
}

private fun Modifier.androidxClipBg(color: androidx.compose.ui.graphics.Color) =
    this.then(Modifier.androidxBg(color))

private fun Modifier.androidxBg(color: androidx.compose.ui.graphics.Color) =
    this.then(Modifier.background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(16.dp))

@Composable
fun DeleteAccountScreen(s: AppState, vm: AppViewModel) {
    var typed by remember { mutableStateOf("") }
    Page(
        title = "Delete my account", onBack = vm::back,
        actions = { if (typed.trim().equals("DELETE", true) && !s.busy) DestructiveButton("Delete everything", { vm.deleteAccount() }) }
    ) {
        SafetyPanel("This permanently deletes your profile, conditions, plans and photos, check-ins, exercise logs, medicines and settings from this phone and from our server. It cannot be undone.", title = "Step 1: what will be deleted")
        Text("Step 2: confirm", style = MaterialTheme.typography.titleSmall)
        LabeledField("Type DELETE to confirm", typed, { typed = it })
        Hint("Tip: use \"Export my data\" first if you want a copy.")
        Busy(s.busy, "Deleting…"); ErrorText(s.error)
    }
}
