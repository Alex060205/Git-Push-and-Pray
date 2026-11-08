package com.example.vitala.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vitala.app.*
import com.example.vitala.data.StopReason
import com.example.vitala.data.SymptomResponse
import com.example.vitala.pacing.adaptive.DoseDimension
import com.example.vitala.ui.theme.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private fun greeting(): String {
    val h = LocalTime.now().hour
    return if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"
}

private fun doseSummary(items: List<TodayItem>, day: DayRec, upTo: Boolean): String {
    val minutes = if (day.pain != null && !day.blocked && day.exercises.isNotEmpty())
        day.exercises.filter { it.dimension == "MINUTES" }.mapNotNull { it.levelDose(day) }.sum()
    else items.filter { it.dimension == DoseDimension.MINUTES }.sumOf { it.upTo ?: 0.0 }
    val other = items.firstOrNull { it.dimension != DoseDimension.MINUTES }
    val text = when {
        minutes > 0 -> Dose.fmt(DoseDimension.MINUTES, minutes)
        other != null -> Dose.fmt(other.dimension, other.upTo ?: 0.0)
        else -> ""
    }
    return if (upTo && text.isNotBlank()) "Up to $text" else text
}

private fun DayExercise.levelDose(day: DayRec): Double? = when (day.levelChosen) {
    Level.SHORTER.name -> minimumValue?.let { m -> doseValue?.let { minOf(it, m) } ?: m }
    Level.REST.name -> 0.0
    else -> doseValue
}

@Composable
fun HomeScreen(s: AppState, vm: AppViewModel, bar: @Composable () -> Unit) {
    val today = LocalDate.now()
    val sessions = vm.sessionsToday()
    val pegDue = vm.pegDue()
    val checkedIn = s.checkIns.any { it.date == today }
    val afterReminder = LocalTime.now() >= s.settings.checkInTime.toTime()
    val dayToday = if (s.day.date == today.toString()) s.day else DayRec()

    LaunchedEffect(s.settings.calendarLinked, s.settings.exerciseTimes, s.programs) { vm.refreshSuggestion() }

    Page(
        bottomBar = bar,
        actions = {
            val label = "Daily check-in"
            if (afterReminder && !checkedIn) PrimaryButton(label, vm::startCheckIn) else SecondaryButton(label, vm::startCheckIn)
            Text(if (checkedIn) "Done today" else "Not done yet · about 3 minutes", style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
    ) {
        Column {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = secondaryText(), style = MaterialTheme.typography.bodySmall)
            Text("${greeting()}${if (s.profile.firstName.isNotBlank()) ", ${s.profile.firstName}" else ""}", style = MaterialTheme.typography.headlineMedium)
        }
        s.message?.let { m -> GreenPanel { Text(m, color = GreenDark, style = MaterialTheme.typography.titleSmall) } }
        ErrorText(s.error)

        if (pegDue) {
            AmberNotice("Weekly check-in is ready", "3 quick questions · about 1 minute") {
                PrimaryButton("Start", { vm.go(Screen.Weekly(0)) })
                LinkButton("Not today", vm::postponePeg)
            }
        }

        SectionLabel("Exercises today")
        when {
            s.activeConditions.isEmpty() -> Panel {
                Text("No conditions yet", style = MaterialTheme.typography.titleSmall)
                Hint("Add your first condition and your clinician's plan to see today's exercises.")
                PrimaryButton("Add your first condition", { vm.go(Screen.AddCondition(false)) })
            }
            sessions.isEmpty() -> Panel {
                Text("No plan yet", style = MaterialTheme.typography.titleSmall)
                Hint("Add your plan on the Plan tab. It stays the boundary for everything we suggest.")
                SecondaryButton("Go to Plan", { vm.selectTab(Tab.PLAN) })
            }
            else -> sessions.forEach { (time, items) -> ExerciseCard(s, vm, time, items, dayToday) }
        }

        s.timeSuggestion?.takeIf { it.usualBusy && it.suggested != null }?.let { sug ->
            Panel(color = GreenTint) {
                Text("Your calendar is busy at ${AppViewModel.fmtTime(sug.usual)}.", style = MaterialTheme.typography.bodySmall)
                LinkButton("See suggested time: ${AppViewModel.fmtTime(sug.suggested!!)}", { vm.go(Screen.SuggestedTime) })
            }
        }
        s.calendarNote?.let { Hint(it) }

        SectionLabel("Medicines")
        MedicinesToday(s, vm)

        LinkButton("Something changed", { vm.go(Screen.SomethingChanged) }, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ExerciseCard(s: AppState, vm: AppViewModel, time: String, items: List<TodayItem>, day: DayRec) {
    val started = items.none { it.notStarted }
    val clock = fmtClock(time)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AccessibilityNew, null, tint = actionColor(), modifier = Modifier.size(32.dp))
            }
            Column {
                Text("Today's exercises", style = MaterialTheme.typography.titleMedium)
                if (!started) Text("Starts ${items.first().compiled.plan.programStart.format(DateTimeFormatter.ofPattern("MMM d"))}", style = MaterialTheme.typography.bodySmall)
                else if (day.logged && day.levelChosen == Level.REST.name) Text("Rest day · $clock", style = MaterialTheme.typography.bodySmall, color = secondaryText())
                else Text("${doseSummary(items, day, upTo = day.pain == null)} · $clock", style = MaterialTheme.typography.bodySmall, color = secondaryText())
            }
        }
        Text(items.joinToString(", ") { it.name } + " · " + (if (items.size == 1) "1 exercise" else "${items.size} exercises"),
            style = MaterialTheme.typography.bodyLarge)
        when {
            day.logged -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = GreenDark); Spacer(Modifier.width(6.dp))
                Text(if (day.levelChosen == Level.REST.name) "Rest day saved. Nothing to make up." else "Logged today", color = GreenDark, style = MaterialTheme.typography.titleSmall)
            }
            day.pain == null -> Hint("Time is confirmed after you rate your pain.")
            day.blocked -> Hint("Please read today's message first.")
        }
        SecondaryButton("See exercises", { vm.go(Screen.ExerciseDetail(time)) })
    }
}

@Composable
private fun MedicinesToday(s: AppState, vm: AppViewModel) {
    val meds = vm.medsToday()
    if (meds.isEmpty()) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("No medicines added yet", style = MaterialTheme.typography.titleSmall)
                    Hint("Add one to get reminders.")
                }
                SecondaryButton("Add", { vm.go(Screen.EditMedicine(null)) }, Modifier.width(110.dp))
            }
        }
        return
    }
    meds.filter { !it.asNeeded }.forEach { m ->
        m.times.sorted().forEach { t ->
            val st = vm.medStatus(m.id, t)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text(listOf(m.dose, m.instructions).filter { it.isNotBlank() }.joinToString(" · ") + " · ${fmtClock(t)}", style = MaterialTheme.typography.bodySmall, color = secondaryText())
                    }
                    // Neutral wording: a missed dose is never shown as an alarm.
                    Text(st, style = MaterialTheme.typography.labelMedium, color = if (st == "Taken") GreenDark else secondaryText())
                }
                if (st == "Due" || st == "Missed" || st == "Later") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("I took it", { vm.logMed(m.id, t, MedAction.TAKEN) }, Modifier.weight(1f))
                        SecondaryButton("Skip", { vm.logMed(m.id, t, MedAction.SKIPPED) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    meds.filter { it.asNeeded }.forEach { m ->
        val n = s.medEvents.count { it.medicationId == m.id && it.date == LocalDate.now().toString() && it.action == MedAction.TAKEN.name }
        Panel {
            Text(m.name, style = MaterialTheme.typography.titleSmall)
            Hint(listOf(m.dose, "only when needed", if (n > 0) "taken $n today" else "").filter { it.isNotBlank() }.joinToString(" · "))
            SecondaryButton("I took one", { vm.logMed(m.id, null, MedAction.TAKEN) })
        }
    }
}

// ------------------------------------------------------------------ exercise detail

@Composable
fun ExerciseDetailScreen(s: AppState, vm: AppViewModel, time: String) {
    val all = vm.sessionsToday()
    val items = all.firstOrNull { it.first == time }?.second ?: all.flatMap { it.second }
    val day = if (s.day.date == LocalDate.now().toString()) s.day else DayRec()
    val single = items.singleOrNull()
    val source = items.firstOrNull()?.let { s.programs[it.plan.id]?.source }
    Page(
        title = single?.name ?: "Today's exercises",
        subtitle = single?.let { "Day ${it.programDay.coerceAtLeast(1)} of ${it.totalDays}" },
        onBack = vm::back,
        actions = {
            if (!day.logged && items.none { it.notStarted }) {
                PrimaryButton("Start", {
                    if (day.pain != null && !day.blocked) vm.go(Screen.SessionToday) else vm.go(Screen.BeforeStart)
                }, icon = Icons.Outlined.PlayArrow)
                if (day.pain != null && !day.blocked) LinkButton("Choose a different level", { vm.go(Screen.ChooseLevel) })
            }
        }
    ) {
        GreenPanel {
            Text("TODAY'S SESSION", color = GreenDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(doseSummary(items, if (day.pain != null) day else DayRec(), upTo = true).ifBlank { "Rest" }, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("The most your plan allows today. Time includes short rests.", style = MaterialTheme.typography.bodySmall, color = secondaryText(), textAlign = TextAlign.Center)
        }
        if (day.logged) GreenPanel { Text("Done for today. Well done for checking in.", color = GreenDark, style = MaterialTheme.typography.titleSmall) }
        if (items.any { it.notStarted }) AmberNotice(text = "Your plan starts on ${items.first().compiled.plan.programStart.format(DateTimeFormatter.ofPattern("MMMM d"))}.")
        SectionLabel("How to do it")
        items.forEach { item ->
            val ex = s.programs[item.plan.id]?.source?.exercises?.getOrNull(item.compiled.sourceIndex)
            Panel {
                if (items.size > 1) Text(item.name, style = MaterialTheme.typography.titleSmall)
                if (ex != null) {
                    if (ex.setup.isNotBlank()) Text(ex.setup, style = MaterialTheme.typography.bodyLarge)
                    ex.steps.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(actionColor()), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                            Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                    if (ex.pace.isNotBlank()) Hint(ex.pace)
                } else Text(item.compiled.plan.fixedInstructions, style = MaterialTheme.typography.bodyLarge)
            }
        }
        source?.let { rx -> PlanReview.safetyLine(rx)?.let { SafetyPanel(it) } }
    }
}

@Composable
fun BeforeStartScreen(s: AppState, vm: AppViewModel) {
    val areas = vm.todayItems().mapNotNull { s.programs[it.plan.id]?.source?.bodyArea?.takeIf { a -> a.isNotBlank() } }.distinct()
    val title = if (areas.size == 1) "How much familiar ${areas.first()} pain do you have right now?" else "How much familiar pain do you have right now?"
    var pain by rememberSaveable { mutableStateOf(s.pendingPain) }
    val questions = vm.safetyQuestions()
    Page(
        title = title, subtitle = "Rate it before every session, at about the same time each day.",
        onBack = vm::back, topRight = "Before you start",
        actions = {
            PrimaryButton("Next", {
                pain?.let { p ->
                    vm.setPendingPain(p)
                    if (questions.isEmpty()) vm.submitPreSession(p, emptySet()) else vm.go(Screen.SafetyQuestions)
                }
            }, enabled = pain != null && !s.busy)
        }
    ) {
        NumberGrid(pain, { pain = it }, "0 = No pain", "10 = Worst imaginable")
        Busy(s.busy); ErrorText(s.error)
    }
}

@Composable
fun SafetyQuestionsScreen(s: AppState, vm: AppViewModel) {
    val questions = vm.safetyQuestions()
    var ticked by remember { mutableStateOf(setOf<String>()) }
    Page(
        title = "Anything like this today?", subtitle = "Tick any that apply. Leave them empty if none do. These come from your plan.",
        onBack = vm::back,
        actions = { PrimaryButton("Next", { vm.submitPreSession(s.pendingPain ?: 0, ticked) }, enabled = !s.busy) }
    ) {
        questions.forEach { q ->
            CheckCard(q in ticked, q, { on -> ticked = if (on) ticked + q else ticked - q })
        }
        Busy(s.busy); ErrorText(s.error)
    }
}

@Composable
fun SessionTodayScreen(s: AppState, vm: AppViewModel) {
    val d = s.day
    val exercises = d.exercises.filter { it.exerciseId.isNotBlank() }
    var answered by rememberSaveable { mutableStateOf(false) }
    val increase = exercises.filter { it.action == "INCREASE" }
    val needAnswer = increase.isNotEmpty() && !answered
    val total = exercises.filter { it.dimension == "MINUTES" }.sumOf { it.doseValue ?: 0.0 }
    val big = if (exercises.size == 1) exercises.first().doseValue?.let { Dose.fmt(exercises.first().dimension, it) }.orEmpty()
    else if (total > 0) Dose.fmt(DoseDimension.MINUTES, total) else "Today's session"
    Page(
        title = "Your session today", onBack = vm::back,
        actions = {
            PrimaryButton("Start timer", { vm.startSession(Level.PLANNED) { vm.go(Screen.Timer) } }, enabled = !needAnswer && !s.busy)
            LinkButton("Choose a different level", { vm.go(Screen.ChooseLevel) })
        }
    ) {
        GreenPanel {
            Text("TODAY'S SESSION", color = GreenDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(big, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            if (exercises.size > 1) exercises.forEach { Text("${it.name}: ${it.doseValue?.let { v -> Dose.fmt(it.dimension, v) } ?: ""}", style = MaterialTheme.typography.bodySmall) }
            Text("Once today · includes short rests", style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
        if (increase.isNotEmpty()) {
            Panel(color = AmberTint) {
                Text("Your plan allows an increase", style = MaterialTheme.typography.titleSmall, color = AmberText)
                Text("Your plan allows ${increase.joinToString(" and ") { it.doseValue?.let { v -> Dose.fmt(it.dimension, v) }.orEmpty() }} today. Ready to try it?", color = AmberText)
                if (needAnswer) {
                    PrimaryButton("Yes, try it", { answered = true })
                    SecondaryButton("Not today", { vm.declineIncrease(); answered = true })
                } else Hint("Your choice is saved.")
            }
        }
        Panel {
            Text("Why ${big.ifBlank { "this" }}?", style = MaterialTheme.typography.titleMedium)
            exercises.map { it.reason }.distinct().forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            exercises.map { it.note }.filter { it.isNotBlank() }.distinct().forEach { Hint(it) }
        }
        Hint("You choose. Any level your plan allows is fine, including resting.")
        Busy(s.busy); ErrorText(s.error)
    }
}

@Composable
fun ChooseLevelScreen(s: AppState, vm: AppViewModel) {
    val ex = s.day.exercises.filter { it.exerciseId.isNotBlank() }
    fun text(level: Level): String {
        val minutes = ex.filter { it.dimension == "MINUTES" }.mapNotNull { vm.targetFor(it, level) }.sum()
        return if (minutes > 0) Dose.fmt(DoseDimension.MINUTES, minutes) else ex.firstOrNull()?.let { e -> vm.targetFor(e, level)?.let { Dose.fmt(e.dimension, it) } }.orEmpty()
    }
    val planned = text(Level.PLANNED); val shorter = text(Level.SHORTER)
    val showShorter = ex.any { e -> (e.minimumValue ?: Double.MAX_VALUE) < (e.doseValue ?: 0.0) }
    var choice by remember { mutableStateOf(Level.PLANNED) }
    Page(
        title = "Today's level", subtitle = "We suggest $planned. You choose.", onBack = vm::back,
        actions = {
            PrimaryButton(if (choice == Level.REST) "Save a rest day" else "Start: ${text(choice)}", {
                if (choice == Level.REST) vm.startSession(Level.REST) { vm.toHome() } else vm.startSession(choice) { vm.go(Screen.Timer) }
            }, enabled = !s.busy)
        }
    ) {
        OptionCard(choice == Level.PLANNED, "Planned: $planned", { choice = Level.PLANNED }, "As planned. Slow, comfortable, with short rests.", badge = "Suggested")
        if (showShorter) OptionCard(choice == Level.SHORTER, "Shorter: $shorter", { choice = Level.SHORTER }, "The smallest session your plan allows.")
        OptionCard(choice == Level.REST, "Rest today", { choice = Level.REST }, "Skip the session. It is logged as missed and is not made up later.")
        Hint("Only options from your plan are shown.")
        Busy(s.busy); ErrorText(s.error)
    }
}

@Composable
fun TimerScreen(s: AppState, vm: AppViewModel) {
    val targetSeconds = vm.timerMinutes() * 60
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(true) }
    val countdown = targetSeconds > 0
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    LaunchedEffect(running) {
        while (running && (!countdown || elapsed < targetSeconds)) { delay(1000); elapsed++ }
    }
    val finished = countdown && elapsed >= targetSeconds
    val shown = if (countdown) (targetSeconds - elapsed).coerceAtLeast(0) else elapsed
    val names = s.day.exercises.filter { it.exerciseId.isNotBlank() }.joinToString(", ") { it.name }
    val primary = actionColor()
    Page(
        onBack = null, topRight = names,
        actions = {
            if (!finished) SecondaryButton(if (running) "Pause" else "Resume", { running = !running })
            PrimaryButton("Finish session", { vm.finishTimer(elapsed); vm.replaceTop(Screen.ExerciseLog(false)) })
        }
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    val stroke = Stroke(width = 22f, cap = StrokeCap.Round)
                    drawArc(Color(0xFFD5DCE2), 0f, 360f, false, style = stroke, topLeft = Offset(11f, 11f), size = Size(size.width - 22f, size.height - 22f))
                    val frac = if (countdown) (elapsed.toFloat() / targetSeconds).coerceIn(0f, 1f) else (elapsed % 60) / 60f
                    drawArc(primary, -90f, 360f * frac, false, style = stroke, topLeft = Offset(11f, 11f), size = Size(size.width - 22f, size.height - 22f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%d:%02d".format(shown / 60, shown % 60), style = MaterialTheme.typography.headlineLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(52f, androidx.compose.ui.unit.TextUnitType.Sp)))
                    Text(if (countdown) "left of ${targetSeconds / 60}:00" else "so far", style = MaterialTheme.typography.bodySmall, color = secondaryText())
                }
            }
        }
        if (finished) GreenPanel { Text("Time's up. Tap Finish when you're ready.", color = GreenDark, style = MaterialTheme.typography.titleSmall) }
        s.day.exercises.firstOrNull { it.instructions.isNotBlank() }?.let { Panel { Text(it.instructions.lines().firstOrNull { l -> l.isNotBlank() }.orEmpty(), style = MaterialTheme.typography.bodyLarge) } }
        val rx = s.programs.values.firstOrNull()?.source
        SafetyPanel(rx?.let { r -> r.stopRules.firstOrNull { it.level == com.example.vitala.Recognition.StopLevel.PAUSE }?.trigger?.let { "Pause: $it" } } ?: "Pause if discomfort keeps building or feels different from usual.")
        Hint("A timer alone doesn't count as exercise. You'll confirm what you really did.")
    }
}

// ------------------------------------------------------------------ logging

@Composable
fun ExerciseLogContent(s: AppState, vm: AppViewModel, assignedOnly: Boolean = true) {
    val ex = s.day.exercises.filter { it.exerciseId.isNotBlank() }
    val l = s.log
    val anyShort = ex.any { e -> (l.actuals[e.exerciseId] ?: 0.0) < (e.doseValue ?: 0.0) }
    ex.forEach { e ->
        val assigned = e.doseValue ?: 0.0
        val value = l.actuals[e.exerciseId] ?: assigned
        val unit = when (e.dimension) { "MINUTES" -> "min"; "REPETITIONS" -> "reps"; "INTENSITY" -> "level"; else -> "kg" }
        val label = (if (e.dimension == "MINUTES") "Minutes done" else "Amount done") + (if (e.dimension == "MINUTES") "\nincluding short rests" else "")
        Stepper(
            value = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value), unit = unit, label = label,
            onMinus = { vm.updateLog { copy(actuals = actuals + (e.exerciseId to maxOf(0.0, value - 1))) } },
            onPlus = { vm.updateLog { copy(actuals = actuals + (e.exerciseId to minOf(assigned, value + 1))) } }
        )
        if (ex.size > 1) Hint(e.name)
    }
    Text("How did it feel afterwards?", style = MaterialTheme.typography.titleSmall)
    listOf(
        SymptomResponse.WITHIN_RANGE to "Familiar, within my agreed range",
        SymptomResponse.BEYOND_RANGE to "Familiar, more than agreed",
        SymptomResponse.NEW_OR_DIFFERENT to "New or different"
    ).forEach { (v, t) -> OptionCard(l.symptom == v, t, { vm.updateLog { copy(symptom = v) } }) }
    if (l.symptom == SymptomResponse.NEW_OR_DIFFERENT) {
        // EX-6: the plan's own review instruction is shown right away.
        val contact = ex.firstOrNull { it.clinicContact.isNotBlank() }?.clinicContact
        SafetyPanel(
            "Stop the exercise and contact your clinic before doing more." + (contact?.let { " $it." } ?: "") + " " + Safety.GENERIC,
            title = "New or different symptoms"
        )
    }
    Text("Any new symptoms?", style = MaterialTheme.typography.titleSmall)
    val options = listOf("None", "Numbness", "Tingling", "Weakness", "Swelling", "Other")
    options.chunked(2).forEach { row ->
        ChipRow {
            row.forEach { o ->
                ChoiceChip(o in l.newSymptoms, o, {
                    vm.updateLog {
                        copy(newSymptoms = when {
                            o == "None" -> setOf("None")
                            o in newSymptoms -> newSymptoms - o
                            else -> (newSymptoms - "None") + o
                        })
                    }
                }, Modifier.weight(1f))
            }
        }
    }
    if (anyShort) {
        Text("What got in the way? (optional)", style = MaterialTheme.typography.titleSmall)
        val reasons = listOf(StopReason.PAIN to "Discomfort", StopReason.FATIGUE to "Fatigue", StopReason.NO_TIME to "Practical reason", StopReason.OTHER to "Other")
        reasons.chunked(2).forEach { row ->
            ChipRow { row.forEach { (r, t) -> ChoiceChip(l.stopReason == r, t, { vm.updateLog { copy(stopReason = r) } }, Modifier.weight(1f)) } }
        }
        ex.firstOrNull { it.reason.isNotBlank() }?.let { Hint("If you stopped early, follow your plan's note for a short session. A practical reason never lowers your plan.") }
    }
}

@Composable
fun ExerciseLogScreen(s: AppState, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.prepareLog() }
    Page(
        title = "Your exercise today", subtitle = s.day.exercises.firstOrNull { it.exerciseId.isNotBlank() }?.name,
        onBack = vm::back,
        actions = {
            PrimaryButton("Save", { vm.submitLog { vm.toHome() } }, enabled = !s.busy && s.log.symptom != null)
            LinkButton("I didn't do it today", { vm.updateLog { copy(notDone = true) }; vm.submitLog { vm.toHome() } })
        }
    ) {
        ExerciseLogContent(s, vm)
        Busy(s.busy); ErrorText(s.error)
    }
}

@Composable
fun SafetyBlockScreen(s: AppState, vm: AppViewModel) {
    val ex = s.day.exercises.firstOrNull { it.action in DayRec.BLOCKING }
    val (title, tone) = when (ex?.action) {
        "URGENT" -> "Seek urgent care" to true
        "STOP_AND_ASSESS" -> "Stop the exercise and get assessed" to true
        "PAUSE" -> "Pause the exercise today" to true
        "REVIEW" -> "Contact your clinic before going further" to true
        "NOT_STARTED" -> "Your plan hasn't started yet" to false
        else -> "We need one more answer" to false
    }
    Page(
        title = title, onBack = null,
        actions = {
            PrimaryButton("Back to Home", vm::toHome)
            if (ex?.action != "NOT_STARTED") LinkButton("I answered by mistake", { vm.clearToday(); vm.showResult(Screen.BeforeStart) })
        }
    ) {
        if (tone) SafetyPanel(ex?.instructions?.ifBlank { null } ?: ex?.reason.orEmpty(), title = "From your plan")
        else Text(ex?.reason.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        ex?.clinicContact?.takeIf { it.isNotBlank() }?.let { Panel { Text("Who to contact", style = MaterialTheme.typography.titleSmall); Text(it) } }
        if (tone) SafetyPanel(Safety.GENERIC)
        if (tone) Hint("No change to your exercise is suggested until this is looked at. Nothing is lost: your answers are saved.")
    }
}

// ------------------------------------------------------------------ suggested time

@Composable
fun SuggestedTimeScreen(s: AppState, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.refreshSuggestion() }                       // CAL-7
    val sug = s.timeSuggestion
    var everyDay by remember { mutableStateOf(false) }
    var showOthers by remember { mutableStateOf(false) }
    val group = vm.sessionsToday().firstOrNull()?.second.orEmpty()
    Page(
        title = "Suggested time", subtitle = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), onBack = vm::back,
        actions = {
            if (sug?.suggested != null) {
                PrimaryButton("Use ${AppViewModel.fmtTime(sug.suggested)}", { vm.acceptTime(sug.suggested, everyDay); vm.back() })
                SecondaryButton("Keep ${AppViewModel.fmtTime(sug.usual)}", vm::back)
                LinkButton("See other times", { showOthers = !showOthers })
            } else SecondaryButton("Back", vm::back)
        }
    ) {
        if (sug == null) { Text(s.calendarNote ?: "No suggestion right now. Your usual time is fine."); return@Page }
        Panel {
            Text(group.joinToString(", ") { it.name }, style = MaterialTheme.typography.titleSmall)
            Text("Your time: ${AppViewModel.fmtTime(sug.usual)}")
            Hint(if (sug.usualBusy) "Your calendar shows you are busy then." else "You look free then. Nothing to change.")
        }
        sug.suggested?.let {
            GreenPanel {
                Text("WE SUGGEST", color = GreenDark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(AppViewModel.fmtTime(it), style = MaterialTheme.typography.headlineLarge)
                if (sug.freeFrom != null && sug.freeTo != null) Text("You look free from ${AppViewModel.fmtTime(sug.freeFrom)} to ${AppViewModel.fmtTime(sug.freeTo)}.", style = MaterialTheme.typography.bodySmall)
            }
            CheckCard(everyDay, "Use this time every day", { everyDay = it })
        }
        if (showOthers) sug.others.forEach { t ->
            SecondaryButton("Use ${AppViewModel.fmtTime(t)}", { vm.acceptTime(t, everyDay); vm.back() })
        }
        Hint("Only your busy and free times are used. Event names stay private.")
        Hint("Your exercises and doses never change because of your calendar.")
    }
}

// ------------------------------------------------------------------ reminder screens

@Composable
fun MedicineReminderScreen(s: AppState, vm: AppViewModel, medId: String, time: String) {
    val m = s.meds.firstOrNull { it.id == medId }
    Page(onBack = vm::back, topRight = fmtClock(time),
        actions = {
            PrimaryButton("I took it", { vm.logMed(medId, time, MedAction.TAKEN); vm.toHome() }, icon = Icons.Filled.Check)
            SecondaryButton("Remind me in 15 minutes", { vm.snoozeMedicine(medId, time); vm.toHome() })
            LinkButton("Skip this time", { vm.logMed(medId, time, MedAction.SKIPPED); vm.toHome() })
        }
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Medication, null, tint = actionColor(), modifier = Modifier.size(60.dp))
            }
        }
        Text("Time for your medicine", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Panel {
            Text(m?.name ?: "Your medicine", style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(m?.dose, m?.instructions?.ifBlank { null }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
    }
}

@Composable
fun ExerciseReminderScreen(s: AppState, vm: AppViewModel, time: String) {
    val items = vm.sessionsToday().firstOrNull { it.first == time }?.second.orEmpty()
    Page(onBack = vm::back, topRight = AppViewModel.fmtTime(LocalTime.now()),
        actions = {
            PrimaryButton("Start now", { vm.resetTo(Screen.TabRoot(Tab.HOME), Screen.ExerciseDetail(time)) })
            SecondaryButton("Remind me in 15 minutes", { vm.snoozeExercise(time); vm.toHome() })
            LinkButton("Change the time", { vm.go(Screen.ReminderTimes(false, false)) })
        }
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AccessibilityNew, null, tint = actionColor(), modifier = Modifier.size(60.dp))
            }
        }
        Text("Exercise time at ${fmtClock(time)}", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Panel {
            Text(items.joinToString(", ") { it.name }.ifBlank { "Your exercise" }, style = MaterialTheme.typography.titleMedium)
            val sum = items.filter { it.dimension == DoseDimension.MINUTES }.sumOf { it.upTo ?: 0.0 }
            if (sum > 0) Text("Up to ${Dose.fmt(DoseDimension.MINUTES, sum)} today", style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
    }
}

@Composable
fun SomethingChangedScreen(s: AppState, vm: AppViewModel) {
    var types by remember { mutableStateOf(setOf<ContextType>()) }
    var note by remember { mutableStateOf("") }
    Page(
        title = "Anything different lately?", subtitle = "Tick any that apply. This helps explain your numbers.", onBack = vm::back,
        actions = { PrimaryButton("Save", { vm.saveContext(types, note) }) }
    ) { ContextForm(types, { types = it }, note, { note = it }) }
}

@Composable
fun ContextForm(types: Set<ContextType>, onTypes: (Set<ContextType>) -> Unit, note: String, onNote: (String) -> Unit) {
    ContextType.entries.forEach { t ->
        CheckCard(t in types, t.label, { on -> onTypes(if (on) types + t else types - t) })
    }
    LabeledField("Add a note (optional)", note, onNote, singleLine = false, minLines = 3)
}
