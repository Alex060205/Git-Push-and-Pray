package com.example.vitala.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vitala.app.*
import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.RecoveryStatus
import com.example.vitala.data.RecoveryTime
import com.example.vitala.data.SessionStatus
import com.example.vitala.report.ReportSaver
import com.example.vitala.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Metric(val label: String) { PAIN("Pain"), FATIGUE("Fatigue"), DAILY("Daily life"), RECOVERY("Recovery") }

private fun DailyCheckIn.value(m: Metric): Int? = when (m) {
    Metric.PAIN -> painLevel; Metric.FATIGUE -> fatigue; Metric.DAILY -> interference; Metric.RECOVERY -> null
}

private fun recoveryText(c: DailyCheckIn?): String = when (c?.recovery?.status) {
    RecoveryStatus.RETURNED -> "Back to normal" + (c.recovery?.time?.let { ", ${it.label}" } ?: "")
    RecoveryStatus.STILL_ABOVE -> "Still more than normal"
    RecoveryStatus.UNSURE -> "Not sure"
    null -> "Not asked"
}

@Composable
private fun RecoveryMark(c: DailyCheckIn?, compact: Boolean) {
    val (icon, label) = when (c?.recovery?.status) {
        RecoveryStatus.RETURNED -> Icons.Filled.Check to "Normal"
        RecoveryStatus.STILL_ABOVE -> Icons.Outlined.ArrowUpward to "Above"
        RecoveryStatus.UNSURE -> Icons.Outlined.HelpOutline to "Unsure"
        null -> null to "–"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) Icon(icon, null, modifier = Modifier.size(if (compact) 18.dp else 26.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun monday(d: LocalDate) = d.with(DayOfWeek.MONDAY)

@Composable
fun InsightsScreen(s: AppState, vm: AppViewModel, bar: @Composable () -> Unit) {
    var monthView by rememberSaveable { mutableStateOf(false) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var metricName by rememberSaveable { mutableStateOf(Metric.PAIN.name) }
    val metric = Metric.valueOf(metricName)
    val today = LocalDate.now()
    val byDate = s.checkIns.associateBy { it.date }
    val anchorDate = LocalDate.ofEpochDay(anchor)
    val selectedDate = LocalDate.ofEpochDay(selected)

    Page(
        title = "Insights", bottomBar = bar,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(if (monthView) "Week view" else "Month view", { monthView = !monthView }, Modifier.weight(1f))
                PrimaryButton("Export PDF", { vm.go(Screen.ExportPdf) }, Modifier.weight(1f))
            }
        }
    ) {
        // period header
        val header = if (monthView) "${YearMonth.from(anchorDate).month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${anchorDate.year}"
        else { val m = monday(anchorDate); "${m.format(DateTimeFormatter.ofPattern("MMM d"))} - ${m.plusDays(6).dayOfMonth}" }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavCircle(Icons.AutoMirrored.Outlined.ArrowBack, "Previous") { anchor = (if (monthView) anchorDate.minusMonths(1) else anchorDate.minusWeeks(1)).toEpochDay() }
            Text(header, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            NavCircle(Icons.AutoMirrored.Outlined.ArrowForward, "Next") { anchor = (if (monthView) anchorDate.plusMonths(1) else anchorDate.plusWeeks(1)).toEpochDay() }
        }
        if (!monthView) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..6).map { monday(anchorDate).plusDays(it.toLong()) }.forEach { d ->
                    val sel = d == selectedDate
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(if (sel) actionColor() else Color.White)
                            .border(if (d == today) 3.dp else 1.dp, if (d == today) actionColor() else borderColor(), RoundedCornerShape(4.dp))
                            .clickable { selected = d.toEpochDay() }.padding(vertical = 8.dp)
                            .semantics { contentDescription = d.format(DateTimeFormatter.ofPattern("EEEE d")) + if (d == today) ", today" else "" },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.bodySmall, color = if (sel) Color.White else secondaryText())
                        Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleSmall, color = if (sel) Color.White else TextPrimary)
                    }
                }
            }
        }
        MetricTabs(metric) { metricName = it.name }

        if (!monthView) {
            WeekBars(anchorDate, selectedDate, metric, byDate) { selected = it.toEpochDay() }
            DaySummary(s, selectedDate, byDate[selectedDate]) { vm.go(Screen.DayDetail(selectedDate)) }
            PegLine(s)
        } else {
            MonthGrid(anchorDate, metric, byDate) { vm.go(Screen.DayDetail(it)) }
            Legend()
            Hint("Average value each day. Tap a day for details. A dash means nothing was recorded.")
        }
    }
}

@Composable
private fun NavCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).border(2.dp, actionColor(), androidx.compose.foundation.shape.CircleShape)
            .semantics { contentDescription = desc }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = actionColor()) }
}

@Composable
private fun MetricTabs(selected: Metric, onSelect: (Metric) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Metric.entries.forEach { m ->
            val sel = m == selected
            Box(
                Modifier.weight(1f).heightIn56().clip(RoundedCornerShape(4.dp)).background(if (sel) Color(0xFFE5F1F9) else Color.White)
                    .border(if (sel) 3.dp else 1.dp, if (sel) actionColor() else borderColor(), RoundedCornerShape(4.dp))
                    .clickable { onSelect(m) },
                contentAlignment = Alignment.Center
            ) { Text(m.label, style = MaterialTheme.typography.labelMedium, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center) }
        }
    }
}

private fun Modifier.heightIn56() = this.then(Modifier.height(56.dp))

@Composable
private fun WeekBars(anchor: LocalDate, selected: LocalDate, metric: Metric, byDate: Map<LocalDate, DailyCheckIn>, onPick: (LocalDate) -> Unit) {
    Panel(color = Color(0xFFEAF2F8)) {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            (0..6).map { monday(anchor).plusDays(it.toLong()) }.forEach { d ->
                val c = byDate[d]
                val v = c?.value(metric)
                Column(Modifier.weight(1f).clickable { onPick(d) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (metric == Metric.RECOVERY) {
                        RecoveryMark(c, true)
                    } else {
                        Text(v?.toString() ?: "–", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (v != null) Box(
                            Modifier.fillMaxWidth().height((8 + v * 10).dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (d == selected) BrandBlueDark else Color(0xFF8CBFE0))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySummary(s: AppState, date: LocalDate, c: DailyCheckIn?, onOpen: () -> Unit) {
    Panel {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.titleMedium)
        if (c == null) Text("No check-in recorded for this day.")
        else {
            SummaryLine("Pain average", "${c.painLevel}")
            SummaryLine("Fatigue", "${c.fatigue}")
            SummaryLine("Daily life", "${c.interference}")
            SummaryLine("Recovery", recoveryText(c))
        }
        sessionLine(s, date)?.let { SummaryLine("Exercise", it) }
        val meds = s.medEvents.filter { it.date == date.toString() }
        if (meds.isNotEmpty()) SummaryLine("Medicines", "${meds.count { it.action == MedAction.TAKEN.name }} taken, ${meds.count { it.action == MedAction.SKIPPED.name }} skipped")
        SecondaryButton("See day details", onOpen)
    }
}

private fun sessionLine(s: AppState, date: LocalDate): String? {
    val ses = s.sessions.firstOrNull { it.date == date.toString() } ?: return null
    val what = when (ses.status) {
        SessionStatus.DONE -> "Done as planned"
        SessionStatus.OVERDONE -> "More than planned"
        SessionStatus.CUT_SHORT, SessionStatus.INCOMPLETE -> "Shortened"
        SessionStatus.SKIPPED -> "Not done (logged as missed)"
        SessionStatus.PLANNED -> "Planned"
    }
    val mins = ses.items.sumOf { it.durationMinutes ?: 0 }
    val feel = ses.symptomResponse?.let { " · " + it.label }.orEmpty()
    val amount = if (mins > 0 && ses.status != SessionStatus.SKIPPED) " ($mins min)" else ""
    return what + amount + feel
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PegLine(s: AppState) {
    val sorted = s.pegs.sortedBy { it.date }
    val last = sorted.lastOrNull() ?: return
    val prev = sorted.getOrNull(sorted.size - 2)
    Panel {
        Text("Weekly PEG", style = MaterialTheme.typography.titleSmall)
        Text("Score ${"%.1f".format(last.score)} on ${last.date.format(DateTimeFormatter.ofPattern("MMM d"))} (pain ${last.pain}, enjoyment ${last.enjoyment}, activity ${last.activity})", style = MaterialTheme.typography.bodySmall)
        if (prev != null) Hint("Last time: ${"%.1f".format(prev.score)}. ${if (last.score < prev.score) "A lower number means less impact." else if (last.score > prev.score) "A higher number means more impact." else "No change."}")
    }
}

@Composable
private fun MonthGrid(anchor: LocalDate, metric: Metric, byDate: Map<LocalDate, DailyCheckIn>, onPick: (LocalDate) -> Unit) {
    val ym = YearMonth.from(anchor)
    val first = ym.atDay(1)
    val lead = (first.dayOfWeek.value - 1)
    val cells = lead + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { Text(it.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = secondaryText()) }
    }
    for (r in 0 until rows) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (cI in 0..6) {
                val idx = r * 7 + cI - lead
                if (idx < 0 || idx >= ym.lengthOfMonth()) { Spacer(Modifier.weight(1f).aspectRatio(0.8f)); continue }
                val d = ym.atDay(idx + 1)
                val ci = byDate[d]
                val v = ci?.value(metric)
                val hasData = if (metric == Metric.RECOVERY) ci?.recovery != null else v != null
                Column(
                    Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(4.dp))
                        .background(if (!hasData) NoData else if (v != null) bandColor(v) else Color(0xFFE4EEF5))
                        .border(if (d == LocalDate.now()) 3.dp else 1.dp, if (d == LocalDate.now()) actionColor() else borderColor(), RoundedCornerShape(4.dp))
                        .clickable { onPick(d) }.padding(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = secondaryText(), modifier = Modifier.align(Alignment.Start))
                    if (metric == Metric.RECOVERY) { if (hasData) RecoveryMark(ci, true) else Text("–") }
                    else Text(v?.toString() ?: "–", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("0-1" to Band01, "2-3" to Band23, "4-6" to Band46, "7-8" to Band78, "9-10" to Band910).forEach { (t, c) ->
            Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(4.dp)).background(c).border(1.dp, borderColor(), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                Text(t, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ------------------------------------------------------------------ day detail / edit

@Composable
fun DayDetailScreen(s: AppState, vm: AppViewModel, date: LocalDate) {
    val c = s.checkIns.firstOrNull { it.date == date }
    val rec = s.recs.firstOrNull { it.date == date.toString() }
    val editable = !date.isBefore(LocalDate.now().minusDays(7)) && !date.isAfter(LocalDate.now())      // MS-7
    Page(
        title = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), onBack = vm::back,
        actions = { if (editable) SecondaryButton("Fix an entry for this day", { vm.go(Screen.EditDay(date)) }) }
    ) {
        Panel {
            Text("Pain", style = MaterialTheme.typography.titleMedium)
            if (c == null) Text("Nothing recorded.") else { SummaryLine("Average", "${c.painLevel}"); SummaryLine("Recovery", recoveryText(c)) }
        }
        Panel {
            Text("Fatigue and daily life", style = MaterialTheme.typography.titleMedium)
            if (c == null) Text("Nothing recorded.") else { SummaryLine("Fatigue", "${c.fatigue}"); SummaryLine("Daily life", "${c.interference}") }
        }
        Panel {
            Text("Exercise", style = MaterialTheme.typography.titleMedium)
            Text(sessionLine(s, date) ?: "No session recorded.")
        }
        rec?.takeIf { it.reason.isNotBlank() }?.let {
            Panel {
                Text("Why", style = MaterialTheme.typography.titleMedium)
                Text(it.reason)
                if (it.override) Hint("You chose a different level than suggested.")
            }
        }
        val meds = s.medEvents.filter { it.date == date.toString() && it.action != MedAction.SNOOZED.name }
        if (meds.isNotEmpty()) Panel {
            Text("Medicines", style = MaterialTheme.typography.titleMedium)
            meds.forEach { e ->
                val m = s.meds.firstOrNull { it.id == e.medicationId }
                Text("${m?.name ?: "Medicine"}: ${if (e.action == MedAction.TAKEN.name) "taken" else "skipped"}${e.scheduledTime?.let { " (${fmtClock(it)})" } ?: ""}")
            }
        }
        val ctx = s.contexts.filter { it.date == date.toString() }
        if (ctx.isNotEmpty()) Panel {
            Text("Anything different", style = MaterialTheme.typography.titleMedium)
            ctx.forEach { e ->
                if (e.types.isNotEmpty()) Text(e.types.joinToString(", ") { t -> runCatching { ContextType.valueOf(t).label }.getOrDefault(t) })
                if (e.note.isNotBlank()) Hint(e.note)
            }
        }
    }
}

@Composable
fun EditDayScreen(s: AppState, vm: AppViewModel, date: LocalDate) {
    val old = s.checkIns.firstOrNull { it.date == date }
    var pain by remember { mutableStateOf(old?.painLevel ?: 0) }
    var fatigue by remember { mutableStateOf(old?.fatigue ?: 0) }
    var life by remember { mutableStateOf(old?.interference ?: 0) }
    var rec by remember { mutableStateOf(old?.recovery?.status) }
    var recTime by remember { mutableStateOf(old?.recovery?.time) }
    Page(
        title = "Fix an entry", subtitle = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) + ". The change is logged.", onBack = vm::back,
        actions = { PrimaryButton("Save changes", { vm.editCheckIn(date, pain, fatigue, life, rec, recTime) }, enabled = rec != RecoveryStatus.RETURNED || recTime != null) }
    ) {
        Stepper("$pain", "0 to 10", { pain = maxOf(0, pain - 1) }, { pain = minOf(10, pain + 1) }, "Pain average")
        Stepper("$fatigue", "0 to 10", { fatigue = maxOf(0, fatigue - 1) }, { fatigue = minOf(10, fatigue + 1) }, "Fatigue")
        Stepper("$life", "0 to 10", { life = maxOf(0, life - 1) }, { life = minOf(10, life + 1) }, "Daily life")
        Text("Is your pain back to normal for you?", style = MaterialTheme.typography.titleSmall)
        OptionCard(rec == null, "Not asked", { rec = null; recTime = null })
        OptionCard(rec == RecoveryStatus.RETURNED, "Yes, back to normal", { rec = RecoveryStatus.RETURNED })
        OptionCard(rec == RecoveryStatus.STILL_ABOVE, "Still more than normal", { rec = RecoveryStatus.STILL_ABOVE; recTime = null })
        OptionCard(rec == RecoveryStatus.UNSURE, "Not sure", { rec = RecoveryStatus.UNSURE; recTime = null })
        if (rec == RecoveryStatus.RETURNED) ChipRow {
            listOf(RecoveryTime.SAME_DAY to "Same day", RecoveryTime.NEXT_DAY to "Next day", RecoveryTime.LATER to "Later").forEach { (v, t) ->
                ChoiceChip(recTime == v, t, { recTime = v }, Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ PDF export

@Composable
fun ExportPdfScreen(s: AppState, vm: AppViewModel) {
    val ctx = LocalContext.current
    var saved by remember { mutableStateOf<Pair<ReportSaver.Saved, String>?>(null) }
    Page(
        title = "Report for my ${s.profile.medicalProvider.ifBlank { "doctor" }}", subtitle = "Made on your phone. Nothing is uploaded unless you share it.", onBack = vm::back,
        actions = {
            if (saved == null) PrimaryButton("Create PDF", { vm.exportReport { sv, name -> saved = sv to name } }, enabled = !s.busy)
            else {
                val uri = saved!!.first.uri
                if (uri != null) PrimaryButton("Share or print", {
                    val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share report"))
                })
                SecondaryButton("Done", vm::back)
            }
        }
    ) {
        Text("Time period", style = MaterialTheme.typography.titleMedium)
        OptionCard(true, "Last 4 weeks (28 days)", {}, "The report covers the most recent 4 weeks.")
        Panel {
            Text("Includes", style = MaterialTheme.typography.titleSmall)
            Hint("Your plan, pain before each session, minutes done, adjustments and why, daily pain, fatigue and daily life, weekly PEG, medicines and notes. It says clearly that the answers are patient-reported and not a validated clinical tool (except the PEG).")
        }
        Busy(s.busy, "Making your report…")
        ErrorText(s.error)
        saved?.let { GreenPanel { Text("Report saved", color = GreenDark, style = MaterialTheme.typography.titleSmall); Text(it.first.location, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) } }
    }
}
