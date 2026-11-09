package com.example.vitala.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vitala.app.AppState
import com.example.vitala.app.AppViewModel
import com.example.vitala.app.Screen
import com.example.vitala.app.TextSizeChoice
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AuthScreen(s: AppState, vm: AppViewModel) {
    var creating by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    val canGo = email.isNotBlank() && password.length >= 6 && (!creating || agree) && !s.busy
    Page(
        title = if (creating) "Create your account" else "Welcome back",
        subtitle = if (creating) "Follow your clinician's exercise plan, day by day." else "Sign in to see today's plan.",
        actions = {
            PrimaryButton(if (creating) "Create account" else "Sign in", { if (creating) vm.signUp(email, password) else vm.signIn(email, password) }, enabled = canGo)
            LinkButton(if (creating) "I already have an account" else "Create an account", { creating = !creating })
            if (!creating) LinkButton("Forgot my password", { vm.resetPassword(email) })
            // Only in debug builds: one tap to load Maria Lutke (the demo user from the brief).
            if (com.example.vitala.BuildConfig.DEBUG) SecondaryButton("Use demo user (Maria Lutke)", vm::useDemoUser, enabled = !s.busy)
            if (com.example.vitala.BuildConfig.DEBUG) Hint("Demo mode works offline. Nothing is saved to the cloud.")
        }
    ) {
        LabeledField("Email", email, { email = it.trim() }, keyboard = KeyboardType.Email)
        LabeledField("Password", password, { password = it }, password = true, helper = if (creating) "At least 6 characters." else null)
        if (creating) {
            Panel {
                Text("How we use your information", style = MaterialTheme.typography.titleSmall)
                Hint("We only keep what the app needs: your answers, your plan and your medicines. They are encrypted while stored and sent. " +
                        "We show no ads. You can export or delete everything in Settings. This app supports your clinician's plan; it does not diagnose or treat.")
            }
            CheckCard(agree, "I agree", { agree = it }, "I've read how my information is used and agree to it.")
        }
        Busy(s.busy)
        ErrorText(s.error)
        s.message?.let { Hint(it) }
    }
}

@Composable
fun AboutYouScreen(s: AppState, vm: AppViewModel, onboarding: Boolean) {
    var first by remember { mutableStateOf(s.profile.firstName) }
    var last by remember { mutableStateOf(s.profile.lastName) }
    var provider by remember { mutableStateOf(s.profile.medicalProvider) }
    Page(
        title = if (onboarding) "About you" else "Edit my details",
        subtitle = if (onboarding) "Your name, and your provider if you want to add one." else null,
        steps = if (onboarding) 1 to 2 else null,
        onBack = if (onboarding) null else vm::back,
        topRight = if (onboarding) "Step 1 of 2" else null,
        actions = {
            PrimaryButton(if (onboarding) "Next" else "Save", { vm.saveAboutYou(first, last, provider, onboarding) },
                enabled = first.isNotBlank() && last.isNotBlank())
        }
    ) {
        LabeledField("First name", first, { first = it })
        LabeledField("Last name", last, { last = it })
        LabeledField("Your medical provider", provider, { provider = it },
            helper = "Your doctor, physiotherapist or clinic. Optional. You can change it later.")
    }
}

/** One list that repeats the main Settings values (one source of truth; shown once during onboarding). */
@Composable
fun SetupDayScreen(s: AppState, vm: AppViewModel) {
    var dialog by remember { mutableStateOf<String?>(null) }
    val st = s.settings
    fun row(title: String, value: String, key: String, accent: Boolean = false) = Triple(title, value, key)
    val rows = listOf(
        Triple("Text size", st.textSizeChoice.label, "text"),
        Triple("Height", s.profile.heightCm?.let { "${it.toInt()} cm" } ?: "Add", "height"),
        Triple("Weight", s.profile.weightKg?.let { "%.1f kg".format(it) } ?: "Add", "weight"),
        Triple("Exercise time", fmtClock(st.usualExerciseTime), "exercise"),
        Triple("Exercise reminder", "${st.leadMinutes} min before", "lead"),
        Triple("Daily check-in", fmtClock(st.checkInTime), "daily"),
        Triple("Weekly check-in", DayOfWeek.of(st.weeklyDay).getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + fmtClock(st.weeklyTime), "weekly"),
        Triple("Quiet hours", "${fmtClock(st.quietStart)} to ${fmtClock(st.quietEnd)}", "quiet")
    )
    Page(
        title = "Set up your day",
        subtitle = "We've filled in defaults. Tap any to change it, now or later in Settings.",
        steps = 2 to 2, onBack = { vm.setOnboardingStep(0) }, topRight = "Step 2 of 2",
        actions = { PrimaryButton("Next", { vm.setOnboardingStep(2, Screen.AddCondition(true)) }) }
    ) {
        rows.forEach { (title, value, key) ->
            ListRow(title, onClick = { dialog = key }, trailing = {
                Text(value, color = if (value == "Add") actionColor() else secondaryText(), style = MaterialTheme.typography.titleSmall)
            })
        }
        when (dialog) {
            "text" -> ChoiceDialog("Text size", TextSizeChoice.entries.map { it.name to it.label }, st.textSize, { v -> vm.saveSettings { copy(textSize = v) }; dialog = null }, { dialog = null },
                TextSizeChoice.entries.associate { it.name to it.description })
            "height" -> NumberDialog("Height", "cm", s.profile.heightCm?.toInt()?.toString().orEmpty(), { vm.saveHealth(it, null); dialog = null }, { dialog = null })
            "weight" -> NumberDialog("Weight", "kg", s.profile.weightKg?.toString().orEmpty(), { vm.saveHealth(null, it); dialog = null }, { dialog = null })
            "exercise" -> TimeDialog(st.usualExerciseTime, { dialog = null }, { t -> vm.saveSettings { copy(usualExerciseTime = t) }; dialog = null })
            "lead" -> ChoiceDialog("Remind me", LEAD_OPTIONS, st.leadMinutes.toString(), { v -> vm.saveSettings { copy(leadMinutes = v.toInt()) }; dialog = null }, { dialog = null }, LEAD_DESCRIPTIONS)
            "daily" -> TimeDialog(st.checkInTime, { dialog = null }, { t -> vm.saveSettings { copy(checkInTime = t) }; dialog = null })
            "weekly" -> WeeklyDialog(st.weeklyDay, st.weeklyTime, { d, t -> vm.saveSettings { copy(weeklyDay = d, weeklyTime = t) }; dialog = null }, { dialog = null })
            "quiet" -> QuietDialog(st.quietStart, st.quietEnd, { a, b -> vm.saveSettings { copy(quietStart = a, quietEnd = b, quietOn = true) }; dialog = null }, { dialog = null })
        }
    }
}

@Composable
fun WeeklyDialog(day: Int, time: String, onConfirm: (Int, String) -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var d by remember { mutableStateOf(day) }
    if (step == 0) ChoiceDialog("Which day?", DayOfWeek.entries.map { it.value.toString() to it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }, d.toString(), { d = it.toInt(); step = 1 }, onDismiss,
        DayOfWeek.entries.associate { it.value.toString() to "Your weekly check-in every ${it.getDisplayName(TextStyle.FULL, Locale.getDefault())}." })
    else TimeDialog(time, onDismiss, { t -> onConfirm(d, t) })
}

@Composable
fun QuietDialog(start: String, end: String, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var a by remember { mutableStateOf(start) }
    if (step == 0) TimeDialog(start, onDismiss, { a = it; step = 1 }) else TimeDialog(end, onDismiss, { b -> onConfirm(a, b) })
}

@Composable
fun AddConditionScreen(s: AppState, vm: AppViewModel, onboarding: Boolean) {
    var name by remember { mutableStateOf("") }
    var dx by remember { mutableStateOf("") }
    Page(
        title = "Add a condition",
        onBack = if (onboarding) ({ vm.setOnboardingStep(1) }) else vm::back,
        steps = 1 to 2, topRight = "Step 1 of 2",
        actions = {
            PrimaryButton("Next: add the plan", { vm.addCondition(name, dx, onboarding) }, enabled = name.isNotBlank())
            LinkButton("Skip for now", { if (name.isNotBlank()) vm.addConditionOnly(name, dx, onboarding) else if (onboarding) vm.setOnboardingStep(5, Screen.OnboardMedicines) else vm.back() })
        }
    ) {
        LabeledField("What should we call it?", name, { name = it }, placeholder = "For example: Right hand and wrist")
        LabeledField("Diagnosis from your doctor", dx, { dx = it }, placeholder = "Optional")
        Hint("Next you can add your clinician's plan by photo or by typing. You can also do it later.")
    }
}
