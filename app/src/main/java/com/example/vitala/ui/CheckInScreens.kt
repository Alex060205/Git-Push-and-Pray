package com.example.vitala.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vitala.app.*
import com.example.vitala.data.RecoveryStatus
import com.example.vitala.data.RecoveryTime
import com.example.vitala.ui.theme.*

/** Daily check-in: one question per screen, the same for one or several conditions (MS-1, MS-2). */
@Composable
fun CheckInScreen(s: AppState, vm: AppViewModel, step: Int) {
    val steps = remember(s.day, s.sessions.size) { vm.checkInSteps() }
    val name = steps.getOrNull(step) ?: "context"
    val total = steps.size
    val last = step >= total - 1
    val d = s.draft
    fun next() { if (last) vm.saveCheckIn() else vm.go(Screen.CheckIn(step + 1)) }
    val common: @Composable (String, String?, Boolean, @Composable () -> Unit) -> Unit = { title, sub, canNext, body ->
        Page(
            title = title, subtitle = sub, onBack = vm::back, topRight = "Question ${step + 1} of $total", steps = (step + 1) to total,
            actions = { PrimaryButton(if (last) "Finish check-in" else "Next", ::next, enabled = canNext && !s.busy) }
        ) { body(); Busy(s.busy); ErrorText(s.error) }
    }
    when (name) {
        "pain" -> common("Your pain", "On average, over the past 24 hours", d.pain != null) {
            NumberGrid(d.pain, { v -> vm.updateDraft { copy(pain = v) } }, "0 = No pain", "10 = Worst imaginable")
        }
        "fatigue" -> common("Your tiredness", "On average, over the past 24 hours", d.fatigue != null) {
            NumberGrid(d.fatigue, { v -> vm.updateDraft { copy(fatigue = v) } }, "0 = Not tired", "10 = Worst imaginable")
        }
        "interference" -> common("Daily activities", "How much did pain get in the way of your usual activities over the past 24 hours?", d.interference != null) {
            NumberGrid(d.interference, { v -> vm.updateDraft { copy(interference = v) } }, "0 = Not at all", "10 = Completely stopped me")
        }
        "recovery" -> common("Is your pain back to normal for you?", "Normal means the level you and your clinician agreed is OK.", d.recovery != null && (d.recovery != RecoveryStatus.RETURNED || d.recoveryTime != null)) {
            OptionCard(d.recovery == RecoveryStatus.RETURNED, "Yes, back to normal", { vm.updateDraft { copy(recovery = RecoveryStatus.RETURNED) } })
            OptionCard(d.recovery == RecoveryStatus.STILL_ABOVE, "Still more than normal", { vm.updateDraft { copy(recovery = RecoveryStatus.STILL_ABOVE, recoveryTime = null) } })
            OptionCard(d.recovery == RecoveryStatus.UNSURE, "Not sure", { vm.updateDraft { copy(recovery = RecoveryStatus.UNSURE, recoveryTime = null) } })
            if (d.recovery == RecoveryStatus.RETURNED) {
                Text("When did it settle?", style = MaterialTheme.typography.titleSmall)
                ChipRow {
                    listOf(RecoveryTime.SAME_DAY to "Same day", RecoveryTime.NEXT_DAY to "Next day", RecoveryTime.LATER to "Later").forEach { (v, t) ->
                        ChoiceChip(d.recoveryTime == v, t, { vm.updateDraft { copy(recoveryTime = v) } }, Modifier.weight(1f))
                    }
                }
            }
        }
        "log" -> {
            LaunchedEffect(Unit) { vm.prepareLog() }
            Page(
                title = "Your exercise today", subtitle = s.day.exercises.firstOrNull { it.exerciseId.isNotBlank() }?.name,
                onBack = vm::back, topRight = "Question ${step + 1} of $total", steps = (step + 1) to total,
                actions = {
                    PrimaryButton("Next", { vm.submitLog { vm.go(Screen.CheckIn(step + 1)) } }, enabled = !s.busy && s.log.symptom != null)
                    LinkButton("I didn't do it today", { vm.updateLog { copy(notDone = true) }; vm.submitLog { vm.go(Screen.CheckIn(step + 1)) } })
                }
            ) { ExerciseLogContent(s, vm); Busy(s.busy); ErrorText(s.error) }
        }
        else -> Page(
            title = "Anything different lately?", subtitle = "Tick any that apply. This helps explain your numbers.",
            onBack = vm::back, topRight = "Question ${step + 1} of $total", steps = (step + 1) to total,
            actions = { PrimaryButton("Finish check-in", { vm.saveCheckIn() }, enabled = !s.busy) }
        ) {
            ContextForm(d.context, { v -> vm.updateDraft { copy(context = v) } }, d.note, { v -> vm.updateDraft { copy(note = v) } })
            Busy(s.busy); ErrorText(s.error)
        }
    }
}

@Composable
fun CheckInDoneScreen(s: AppState, vm: AppViewModel) {
    val tomorrow = java.time.LocalDate.now().plusDays(1)
    Page(actions = { PrimaryButton("Done", vm::toHome) }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(GreenTint), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = GreenDark, modifier = Modifier.size(56.dp))
            }
        }
        Text("Check-in saved", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Thank you.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        val lines = vm.tomorrowLines()
        if (lines.isNotEmpty()) {
            SectionLabel("Tomorrow")
            Panel {
                Text(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.titleMedium)
                lines.forEach { l ->
                    val (name, time, dose, note) = l.split("|").let { it + List(4 - it.size) { "" } }
                    Text("$name · ${fmtClock(time)}")
                    if (dose.isNotBlank()) Text("Starts at $dose", color = GreenDark, style = MaterialTheme.typography.titleSmall)
                    if (note.isNotBlank()) Hint(note)
                }
                Text("We confirm your time after you rate your pain.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Hint("You can always choose a different level from your clinician's plan.")
    }
}

// ------------------------------------------------------------------ weekly PEG

private data class PegItem(val question: String, val low: String, val high: String)

/**
 * The three standard PEG items, shown verbatim (the brief requires wording and recall period to stay unmodified).
 */
private val PEG = listOf(
    PegItem("What number best describes your pain on average in the past week?", "0 = No pain", "10 = Pain as bad as you can imagine"),
    PegItem("What number best describes how, during the past week, pain has interfered with your enjoyment of life?", "0 = Does not interfere", "10 = Completely interferes"),
    PegItem("What number best describes how, during the past week, pain has interfered with your general activity?", "0 = Does not interfere", "10 = Completely interferes")
)

@Composable
fun WeeklyScreen(s: AppState, vm: AppViewModel, step: Int) {
    val item = PEG[step.coerceIn(0, 2)]
    val value = s.weeklyDraft[step]
    Page(
        title = item.question, onBack = vm::back, topRight = "Weekly · ${step + 1} of 3", steps = (step + 1) to 3,
        actions = {
            PrimaryButton(if (step == 2) "Finish" else "Next", { if (step == 2) vm.submitPeg() else vm.go(Screen.Weekly(step + 1)) }, enabled = value != null && !s.busy)
        }
    ) {
        NumberGrid(value, { vm.setPeg(step, it) }, item.low, item.high)
        Busy(s.busy); ErrorText(s.error)
    }
}
