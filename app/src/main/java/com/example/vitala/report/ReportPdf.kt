package com.example.vitala.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.vitala.data.PainFeeling
import com.example.vitala.data.PrescribedExercise
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws [ReportData] as an A4 PDF with Android's built-in PdfDocument (no extra libraries).
 * The pain calendar prints the number in every square as well as shading it, so it reads in black and white.
 */
object ReportPdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private val INK = Color.rgb(29, 28, 26)
    private val MUTED = Color.rgb(90, 86, 78)
    private val TEAL = Color.rgb(31, 95, 91)
    private val LIGHT = Color.rgb(213, 234, 230)
    private val WARN = Color.rgb(250, 226, 200)
    private val GRID = Color.rgb(220, 216, 208)

    // Pain shading 0 -> 5 -> 10. Lightness falls the whole way, so it still reads in greyscale.
    private val PAIN_LOW = Color.rgb(247, 239, 227)
    private val PAIN_MID = Color.rgb(227, 154, 99)
    private val PAIN_HIGH = Color.rgb(138, 36, 36)

    private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val shortDate = DateTimeFormatter.ofPattern("MMM d")
    private val dayDate = DateTimeFormatter.ofPattern("EEE MMM d")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    fun write(data: ReportData, out: OutputStream, zone: ZoneId = ZoneId.systemDefault()) {
        val doc = PdfDocument()
        try {
            Writer(doc).apply { render(data, zone) }.finish()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun Writer.render(d: ReportData, zone: ZoneId) {
        val name = d.patient?.name ?: "Patient"
        title("Progress report: $name")
        text("Period: ${d.from.format(dateFmt)} to ${d.to.format(dateFmt)} (28 days)", muted = true)
        text("Created ${d.generatedAt.atZone(zone).format(dateFmt)} at ${d.generatedAt.atZone(zone).format(timeFmt)}", muted = true)
        space(8f)
        noteBox(
            "This report shows information the patient recorded in the Vitala app between appointments. " +
                    "It is patient-reported, not a clinical assessment, and contains no diagnosis or conclusions. " +
                    "Sections marked PRESCRIBED come from the doctor's plan as entered in the app."
        )
        d.sampleNotes.forEach { noteBox(it, WARN) }

        // Patient
        heading("Patient")
        d.patient?.let { p ->
            keyValue("Name", p.name)
            keyValue("Date of birth", birthText(p.birthDate, d.to))
            if (p.gender.isNotBlank()) keyValue("Gender", p.gender)
            keyValue("Height / weight", "%.0f cm · %.1f kg".format(p.heightCm, p.weightKg))
            keyValue("Affected areas", p.affectedJoints.joinToString(", "))
        } ?: text("No patient profile recorded.")

        // Plan
        heading("Prescribed exercises  ·  PRESCRIBED")
        val exercises = d.patient?.exercise.orEmpty()
        if (exercises.isEmpty()) text("No exercises prescribed in the app.")
        else table(
            listOf("Exercise", "Amount", "Days"), listOf(0.38f, 0.30f, 0.32f),
            exercises.map { listOf(it.name, it.amount(), it.daysText()) }
        )

        // Pain
        heading("Daily pain  ·  PATIENT-REPORTED")
        painCalendar(d)
        text(painComparison(d))

        // Last 7 days: fatigue, interference, recovery
        heading("Last 7 days  ·  PATIENT-REPORTED")
        lastWeek(d)

        // PEG
        heading("PEG questionnaire (weekly)  ·  PATIENT-REPORTED")
        pegSection(d)

        // Exercise
        heading("Exercise sessions  ·  PATIENT-REPORTED")
        exerciseSection(d)

        if (d.missingNotes.isNotEmpty()) {
            heading("Missing information")
            d.missingNotes.forEach { text("• $it") }
        }
    }

    // ---------- Text helpers ----------

    private fun birthText(raw: String, asOf: LocalDate): String {
        val birth = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return raw.ifBlank { "Not recorded" }
        val age = Period.between(birth, asOf).years
        // Patient.birthDate defaults to today, so an age under 1 means it was never filled in.
        return if (age < 1) "Not recorded" else "${birth.format(dateFmt)} (age $age)"
    }

    private fun PrescribedExercise.amount(): String = listOfNotNull(
        if (sets != null && reps != null) "$sets × $reps reps" else null,
        durationMinutes?.let { "$it min" }
    ).joinToString(", ")

    private fun PrescribedExercise.daysText(): String =
        if (days.size == 7) "Every day"
        else DayOfWeek.values().filter { it in days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

    private fun PainFeeling?.label() = when (this) {
        PainFeeling.NONE -> "None"
        PainFeeling.SOME -> "Some"
        PainFeeling.A_LOT -> "A lot"
        null -> "—"
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    private fun painComparison(d: ReportData): String {
        val recentStart = d.to.minusDays(6)
        val earlierEnd = d.to.minusDays(7)
        val recent = d.pain.filter { it.date in recentStart..d.to }
        val earlier = d.pain.filter { it.date in d.from..earlierEnd }
        if (recent.isEmpty() || earlier.isEmpty())
            return "Not enough daily pain ratings to compare the last 7 days with the 21 days before."

        // Compare the rounded averages so the printed numbers add up.
        val a = round1(recent.map { it.value }.average())
        val b = round1(earlier.map { it.value }.average())
        val diff = round1(a - b)
        val last7 = "the last 7 days (${recentStart.format(shortDate)} to ${d.to.format(shortDate)})"
        val prev21 = "the previous 21 days (${d.from.format(shortDate)} to ${earlierEnd.format(shortDate)})"
        val sentence = when {
            diff == 0.0 -> "Average daily pain over $last7 was %.1f out of 10, the same as over $prev21.".format(a)
            diff < 0 -> "Average daily pain over $last7 was %.1f out of 10, %.1f points lower than the %.1f average over $prev21.".format(a, -diff, b)
            else -> "Average daily pain over $last7 was %.1f out of 10, %.1f points higher than the %.1f average over $prev21.".format(a, diff, b)
        }
        val gaps = if (recent.size < 7 || earlier.size < 21)
            " Pain was rated on ${recent.size} of 7 and ${earlier.size} of 21 days." else ""
        return sentence + gaps
    }

    /** e.g. "Fatigue over the last 7 days averaged 4.7 out of 10 (rated on 7 of 7 days), ranging from 3 to 7, highest on Nov 3." */
    private fun weekSentence(what: String, ratings: List<Pair<LocalDate, Int>>): String {
        if (ratings.isEmpty()) return "$what was not rated in the last 7 days."
        val high = ratings.maxOf { it.second }
        val low = ratings.minOf { it.second }
        val highDays = ratings.filter { it.second == high }.joinToString(", ") { it.first.format(shortDate) }
        val range = if (high == low) "the same rating, $high, every day it was rated"
        else "ranging from $low to $high, highest on $highDays"
        return "$what over the last 7 days averaged %.1f out of 10 (rated on ${ratings.size} of 7 days), $range."
            .format(ratings.map { it.second }.average())
    }

    // ---------- Last 7 days ----------

    private fun Writer.lastWeek(d: ReportData) {
        text(weekSentence("Fatigue", d.lastWeek.mapNotNull { e -> e.fatigue?.let { e.date to it } }))
        text(weekSentence("Interference with usual daily activities",
            d.lastWeek.mapNotNull { e -> e.interference?.let { e.date to it } }))
        val checks = d.lastWeek.count { it.recovery != null }
        text(
            if (checks == 0) "No recovery check was recorded in the last 7 days. It is only asked while a change or flare is being evaluated."
            else "Recovery was checked on $checks of the last 7 days, while a change or flare was being evaluated."
        )

        val byDate = d.lastWeek.associateBy { it.date }
        table(
            listOf("Date", "Fatigue", "Interference", "Recovery"),
            listOf(0.18f, 0.14f, 0.16f, 0.52f),
            (0L..6L).map { i ->
                val date = d.weekFrom.plusDays(i)
                val e = byDate[date]
                listOf(
                    date.format(dayDate),
                    e?.fatigue?.toString() ?: "—",
                    e?.interference?.toString() ?: "—",
                    e?.recovery?.label ?: "—"
                )
            }
        )
        text(
            "Fatigue and interference are 0-10. Interference is with usual daily activities over the previous 24 hours " +
                    "(0 none, 10 completely prevented them). Recovery: whether symptoms are back within the agreed usual range, " +
                    "and if so, roughly when. — means not recorded.", muted = true
        )
    }

    // ---------- PEG ----------

    private fun Writer.pegSection(d: ReportData) {
        if (d.peg.isEmpty()) { text("No PEG questionnaire was completed in the last 28 days."); return }
        table(
            listOf("Completed", "Pain on average", "Enjoyment of life", "General activity", "PEG score"),
            listOf(0.18f, 0.20f, 0.21f, 0.21f, 0.20f),
            d.peg.map {
                listOf(it.date.format(shortDate), "${it.pain}", "${it.enjoyment}", "${it.activity}", "%.1f".format(it.score))
            }
        )
        if (d.peg.size >= 2) {
            val first = d.peg.first()
            val last = d.peg.last()
            text("PEG score was %.1f on ${first.date.format(shortDate)} and %.1f on ${last.date.format(shortDate)}."
                .format(first.score, last.score))
        }
        text(
            "Each item covers the past week, 0-10: average pain (0 no pain, 10 pain as bad as you can imagine), and how much " +
                    "pain interfered with enjoyment of life and with general activity (0 does not interfere, 10 completely interferes). " +
                    "PEG score is the average of the three.", muted = true
        )
    }

    // ---------- Exercise sessions ----------

    private fun Writer.exerciseSection(d: ReportData) {
        if (d.sessions.isEmpty()) { text("No exercise sessions were recorded in this period."); return }
        keyValue("Sessions finished", "${d.finishedSessions} of ${d.recordedSessions} recorded")

        val incomplete = d.incomplete
        if (incomplete.isEmpty()) keyValue("Skipped or cut short", "None")
        else {
            val skipped = incomplete.count { it.result == SessionResult.SKIPPED }
            val cut = incomplete.count { it.result == SessionResult.CUT_SHORT }
            keyValue("Skipped or cut short", "${incomplete.size} ($skipped skipped, $cut cut short)")
            val reasons = incomplete.groupingBy { it.reason?.label ?: "Not recorded" }.eachCount()
                .entries.sortedByDescending { it.value }
            keyValue("Reasons given", reasons.joinToString(" · ") { "${it.key} ${it.value}" })
        }

        val responses = d.sessions.mapNotNull { it.symptoms }
        if (responses.isNotEmpty()) keyValue(
            "Symptom response after sessions",
            SymptomResponse.values().filter { it in responses }
                .joinToString(" · ") { c -> "${c.label} ${responses.count { it == c }}" }
        )
        space(4f)

        table(
            listOf("Date", "Result", "Pain during", "Pain after", "Symptom response", "Reason"),
            listOf(0.11f, 0.15f, 0.13f, 0.13f, 0.27f, 0.21f),
            d.sessions.map { r ->
                val s = r.session
                listOf(
                    s.date.format(shortDate),
                    r.result.label,
                    s.painDuring.label(),
                    s.painAfter.label(),
                    r.symptoms?.label ?: if (r.attempted) "Not recorded" else "—",
                    r.reason?.label ?: if (r.result == SessionResult.SKIPPED || r.result == SessionResult.CUT_SHORT) "Not recorded" else ""
                )
            },
            emphasis = d.sessions.indices.filter { d.sessions[it].incomplete }.toSet()
        )
        text(
            "Shaded rows in bold were skipped, cut short or have no result recorded. Pain during and after: none, some or a lot. " +
                    "Symptom response is asked right after each session: familiar and within the agreed range, " +
                    "familiar but beyond it, or new or different.", muted = true
        )
    }

    // ---------- Pain calendar ----------

    private fun lerp(from: Int, to: Int, t: Float): Int {
        fun ch(a: Int, b: Int) = (a + (b - a) * t).roundToInt()
        return Color.rgb(ch(Color.red(from), Color.red(to)), ch(Color.green(from), Color.green(to)), ch(Color.blue(from), Color.blue(to)))
    }

    private fun painShade(value: Int): Int {
        val t = value.coerceIn(0, 10) / 10f
        return if (t <= 0.5f) lerp(PAIN_LOW, PAIN_MID, t * 2) else lerp(PAIN_MID, PAIN_HIGH, (t - 0.5f) * 2)
    }

    /** Mon-Sun grid of the 28 days, each square shaded by that day's pain (0-10). Last 7 days outlined. */
    private fun Writer.painCalendar(d: ReportData) {
        val byDate = d.pain.associate { it.date to it.value }
        val lead = d.from.dayOfWeek.value - 1               // Monday = 0
        val rows = (lead + ReportData.DAYS.toInt() + 6) / 7
        val cellW = CONTENT_W / 7f
        val cellH = 34f
        val headerH = 14f
        ensure(headerH + rows * cellH + 80f)

        val c = canvas
        val small = textPaint(8f, MUTED)
        DayOfWeek.values().forEachIndexed { i, day ->
            c.drawText(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), MARGIN + i * cellW + 4f, y + 10f, small)
        }
        val gridTop = y + headerH
        for (i in 0 until ReportData.DAYS.toInt()) {
            val date = d.from.plusDays(i.toLong())
            val col = (lead + i) % 7
            val row = (lead + i) / 7
            val x = MARGIN + col * cellW
            val top = gridTop + row * cellH
            val r = RectF(x + 1.5f, top + 1.5f, x + cellW - 1.5f, top + cellH - 1.5f)
            val value = byDate[date]
            if (value != null) c.drawRoundRect(r, 3f, 3f, fill(painShade(value)))
            c.drawRoundRect(r, 3f, 3f, if (i >= 21) stroke(INK, 1.4f) else stroke(GRID, 0.6f))

            val dark = value != null && value >= 7
            val label = if (i == 0 || date.dayOfMonth == 1) date.format(shortDate) else "${date.dayOfMonth}"
            c.drawText(label, r.left + 4f, r.top + 10f, textPaint(7.5f, if (dark) Color.WHITE else MUTED))
            val number = textPaint(12f, if (dark) Color.WHITE else INK, bold = true).apply { textAlign = Paint.Align.RIGHT }
            c.drawText(value?.toString() ?: "—", r.right - 5f, r.bottom - 6f, number)
        }

        // Legend: one swatch per value 0-10
        val ly = gridTop + rows * cellH + 8f
        val slot = CONTENT_W / 11f
        for (v in 0..10) {
            val lx = MARGIN + v * slot
            val box = RectF(lx, ly, lx + 14f, ly + 10f)
            c.drawRoundRect(box, 2f, 2f, fill(painShade(v)))
            c.drawRoundRect(box, 2f, 2f, stroke(GRID, 0.6f))
            c.drawText("$v", lx + 18f, ly + 8.5f, small)
        }
        y = ly + 18f
        text("Pain scale 0 (no pain) to 10 (worst pain). The number in each square is that day's rating; " +
                "— means no check-in. Dark outline: last 7 days.", muted = true)
    }

    // ---------- Simple flowing page writer ----------

    private fun textPaint(size: Float, color: Int = INK, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = width }
    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }

    private class Writer(private val doc: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var pageNo = 0
        lateinit var canvas: Canvas
        var y = 0f

        init { newPage() }

        fun newPage() {
            page?.let { endPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page!!.canvas
            y = MARGIN
        }

        private fun endPage(p: PdfDocument.Page) {
            p.canvas.drawText("Vitala · patient-reported information · page $pageNo", MARGIN, PAGE_H - 24f, textPaint(8f, MUTED))
            doc.finishPage(p)
        }

        fun finish() { page?.let { endPage(it) }; page = null }

        fun ensure(height: Float) { if (y + height > PAGE_H - MARGIN - 10f) newPage() }

        private fun layout(text: String, paint: TextPaint, width: Float) =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1.5f, 1f).build()

        private fun draw(text: String, paint: TextPaint, x: Float = MARGIN, width: Float = CONTENT_W, after: Float = 4f) {
            val l = layout(text, paint, width)
            ensure(l.height.toFloat())
            canvas.save(); canvas.translate(x, y); l.draw(canvas); canvas.restore()
            y += l.height + after
        }

        fun title(text: String) = draw(text, textPaint(20f, INK, bold = true), after = 6f)
        fun heading(text: String) {
            ensure(60f); y += 12f
            draw(text, textPaint(13f, TEAL, bold = true), after = 2f)
            canvas.drawLine(MARGIN, y, MARGIN + CONTENT_W, y, stroke(GRID, 0.8f)); y += 6f
        }
        fun text(text: String, muted: Boolean = false) = draw(text, textPaint(9.5f, if (muted) MUTED else INK))
        fun space(h: Float) { y += h }

        fun keyValue(key: String, value: String) {
            val k = layout(key, textPaint(9.5f, MUTED), CONTENT_W * 0.42f)
            val v = layout(value, textPaint(9.5f, INK), CONTENT_W * 0.56f)
            val h = max(k.height, v.height).toFloat()
            ensure(h)
            canvas.save(); canvas.translate(MARGIN, y); k.draw(canvas); canvas.restore()
            canvas.save(); canvas.translate(MARGIN + CONTENT_W * 0.44f, y); v.draw(canvas); canvas.restore()
            y += h + 4f
        }

        fun noteBox(text: String, background: Int = LIGHT) {
            val l = layout(text, textPaint(9f, INK), CONTENT_W - 20f)
            ensure(l.height + 16f)
            canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + CONTENT_W, y + l.height + 16f), 6f, 6f, fill(background))
            canvas.save(); canvas.translate(MARGIN + 10f, y + 8f); l.draw(canvas); canvas.restore()
            y += l.height + 22f
        }

        /** Rows whose index is in [emphasis] are bold on a warm background. */
        fun table(headers: List<String>, widths: List<Float>, rows: List<List<String>>, emphasis: Set<Int> = emptySet()) {
            val cols = widths.map { it * CONTENT_W }
            fun row(cells: List<String>, paint: TextPaint, shade: Int?) {
                val layouts = cells.mapIndexed { i, s -> layout(s, paint, cols[i] - 6f) }
                val h = layouts.maxOf { it.height } + 6f
                ensure(h)
                if (shade != null) canvas.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + h, fill(shade))
                var x = MARGIN
                layouts.forEachIndexed { i, l ->
                    canvas.save(); canvas.translate(x + 3f, y + 3f); l.draw(canvas); canvas.restore(); x += cols[i]
                }
                canvas.drawLine(MARGIN, y + h, MARGIN + CONTENT_W, y + h, stroke(GRID, 0.5f))
                y += h
            }
            row(headers, textPaint(8.5f, INK, bold = true), shade = LIGHT)
            rows.forEachIndexed { i, cells ->
                if (i in emphasis) row(cells, textPaint(8.5f, INK, bold = true), shade = WARN)
                else row(cells, textPaint(8.5f), shade = null)
            }
            y += 6f
        }
    }

    /** File name like "Vitala-report-2026-11-08.pdf". */
    fun fileName(today: LocalDate) = "Vitala-report-$today.pdf"
}
