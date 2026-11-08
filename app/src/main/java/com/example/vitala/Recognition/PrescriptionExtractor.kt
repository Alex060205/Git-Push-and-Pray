package com.example.vitala.Recognition

import java.util.Base64
import com.example.vitala.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.vitala.pacing.integration.AdaptivePlanCompiler
import com.example.vitala.pacing.integration.CompiledExercise

/**
 * Sends an exercise prescription (photo, PDF or text) to the Gemini API and
 * turns the JSON reply into an ExercisePrescription.
 *
 * Calls the Gemini REST API directly with GEMINI_API_KEY from local.properties
 * (no Firebase / App Check). The key ships inside the APK, so this is for
 * development and demos only.
 *
 * TO ADD A FIELD: (1) ExercisePrescription.kt, (2) the schema below,
 * (3) PROMPT, (4) parse(), (5) PrescriptionScreen if it should be shown.
 */
class PrescriptionExtractor(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val modelName: String = "gemini-3.6-flash"
) {

    /**
     * One or more photos of the same prescription (JPEG bytes), in page order.
     * All pages go in one request so Gemini reads them as a single sheet.
     */
    suspend fun extractPhotos(pages: List<ByteArray>): ExercisePrescription {
        require(pages.isNotEmpty()) { "No photos to read" }
        val parts = pages.flatMapIndexed { i, jpeg ->
            listOf(textPart("Photo ${i + 1} of ${pages.size}:"), inlinePart(jpeg, "image/jpeg"))
        }
        return run(parts)
    }

    /** PDF bytes, sent as a PDF so Gemini reads the real text, not a picture of it. */
    suspend fun extractPdf(pdf: ByteArray) = run(listOf(inlinePart(pdf, "application/pdf")))

    /** Plain-text prescription. */
    suspend fun extractText(sheet: String) =
        run(listOf(textPart("PRESCRIPTION TEXT START\n$sheet\nPRESCRIPTION TEXT END")))

    private suspend fun run(input: List<JSONObject>): ExercisePrescription {
        check(apiKey.isNotBlank()) { "Missing GEMINI_API_KEY in local.properties" }
        val body = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().apply { input.forEach { put(it) }; put(textPart(PROMPT)) })
            ))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", SCHEMA))
        val reply = JSONObject(post(body.toString()))
        // Thinking models may split the answer over several parts; skip thought
        // summaries and join the rest.
        val parts = reply.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        val text = (0 until (parts?.length() ?: 0))
            .mapNotNull { parts?.optJSONObject(it) }
            .filterNot { it.optBoolean("thought", false) }
            .joinToString("") { it.optString("text", "") }
        if (text.isBlank()) {
            val reason = reply.optJSONObject("promptFeedback")?.optString("blockReason")
                ?: reply.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason")
            throw IllegalStateException("Gemini returned no text" + (reason?.let { " ($it)" } ?: ""))
        }
        return parse(JSONObject(text))
    }

    suspend fun compileAdaptive(
        source: ExercisePrescription,
        exerciseIndex: Int,
        exerciseId: String
    ): CompiledExercise {

        check(apiKey.isNotBlank()) {
            "Missing GEMINI_API_KEY in local.properties"
        }

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(
                                textPart(
                                    AdaptivePlanCompiler.prompt(
                                        source = source,
                                        index = exerciseIndex
                                    )
                                )
                            )
                        )
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0)
                    .put(
                        "responseMimeType",
                        "application/json"
                    )
                    .put(
                        "responseSchema",
                        AdaptivePlanCompiler.schema
                    )
            )

        val reply = JSONObject(
            post(body.toString())
        )

        val candidate = reply
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?: throw IllegalStateException(
                "Gemini returned no configuration candidate."
            )

        require(
            candidate.optString("finishReason") == "STOP"
        ) {
            "Configuration extraction did not finish normally: " +
                    candidate.optString("finishReason")
        }

        val parts = candidate
            .getJSONObject("content")
            .getJSONArray("parts")

        val text = (0 until parts.length())
            .map { parts.getJSONObject(it) }
            .filterNot {
                it.optBoolean("thought", false)
            }
            .joinToString("") {
                it.optString("text", "")
            }

        require(text.isNotBlank()) {
            "Gemini returned an empty configuration."
        }

        return AdaptivePlanCompiler.parse(
            source = source,
            index = exerciseIndex,
            exerciseId = exerciseId,
            json = text
        )
    }
    private suspend fun post(json: String): String = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000 // long PDFs can take a while
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey) // header, so the key stays out of the URL
        }
        try {
            conn.outputStream.use { it.write(json.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                    .getOrNull() ?: text.take(200)
                throw IllegalStateException("Gemini error $code: $msg")
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun textPart(text: String) = JSONObject().put("text", text)

    private fun inlinePart(bytes: ByteArray, mime: String) = JSONObject().put(
        "inlineData",
        JSONObject().put("mimeType", mime).put("data", Base64.getEncoder().encodeToString(bytes))
    )


    // ---------- parsing ----------

    private fun parse(o: JSONObject) = ExercisePrescription(
        programName = o.str("programName"),
        bodyArea = o.str("bodyArea"),
        clinicalContext = o.str("clinicalContext"),
        treatmentGoals = o.str("treatmentGoals"),
        startDate = o.str("startDate"),
        reviewPlan = o.str("reviewPlan"),
        prescriber = o.str("prescriber"),
        clinicContact = o.str("clinicContact"),
        dateApproved = o.str("dateApproved"),
        exercises = o.objects("exercises").map { e ->
            Exercise(
                name = e.str("name"),
                setup = e.str("setup"),
                steps = e.strings("steps"),
                pace = e.str("pace"),
                sets = e.str("sets"),
                reps = e.str("reps"),
                holdOrDuration = e.str("holdOrDuration"),
                frequency = e.str("frequency"),
                intensityOrLoad = e.str("intensityOrLoad"),
                precautions = e.strings("precautions")
            )
        }.filter { it.name.isNotEmpty() || it.steps.isNotEmpty() },
        schedule = o.objects("schedule").map { p ->
            SchedulePhase(p.str("period"), p.str("dose"), p.str("frequency"), p.str("note"))
        },
        limits = o.strings("limits"),
        adjustmentRules = o.objects("adjustmentRules").map { r ->
            AdjustmentRule(r.str("label"), r.str("condition"), r.str("action"))
        },
        trackingInstructions = o.strings("trackingInstructions"),
        stopRules = o.objects("stopRules")
            .map { s -> StopRule(StopLevel.fromWire(s.str("level")), s.str("trigger")) }
            .filter { it.trigger.isNotEmpty() }
            .sortedBy { it.level.ordinal },
        generalPrecautions = o.strings("generalPrecautions"),
        otherNotes = o.strings("otherNotes"),
        unreadable = o.strings("unreadable")
    )

    private fun JSONObject.str(key: String) = optString(key, "").trim()

    private fun JSONObject.strings(key: String): List<String> {
        val a: JSONArray = optJSONArray(key) ?: return emptyList()
        return List(a.length()) { a.getString(it).trim() }.filter { it.isNotEmpty() }
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val a: JSONArray = optJSONArray(key) ?: return emptyList()
        return List(a.length()) { a.getJSONObject(it) }
    }

    // ---------- schema + prompt ----------

    private companion object {
        fun str() = JSONObject().put("type", "STRING")
        fun strList() = JSONObject().put("type", "ARRAY").put("items", str())
        fun arr(items: JSONObject) = JSONObject().put("type", "ARRAY").put("items", items)
        fun obj(vararg props: Pair<String, JSONObject>) = JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
            .put("required", JSONArray(props.map { it.first }))

        val EXERCISE = obj(
            "name" to str(), "setup" to str(), "steps" to strList(), "pace" to str(),
            "sets" to str(), "reps" to str(), "holdOrDuration" to str(),
            "frequency" to str(), "intensityOrLoad" to str(), "precautions" to strList()
        )
        val PHASE = obj("period" to str(), "dose" to str(), "frequency" to str(), "note" to str())
        val RULE = obj("label" to str(), "condition" to str(), "action" to str())
        val STOP = obj("level" to str(), "trigger" to str())

        val SCHEMA: JSONObject = obj(
            "programName" to str(), "bodyArea" to str(), "clinicalContext" to str(),
            "treatmentGoals" to str(), "startDate" to str(), "reviewPlan" to str(),
            "prescriber" to str(), "clinicContact" to str(), "dateApproved" to str(),
            "exercises" to arr(EXERCISE),
            "schedule" to arr(PHASE),
            "limits" to strList(),
            "adjustmentRules" to arr(RULE),
            "trackingInstructions" to strList(),
            "stopRules" to arr(STOP),
            "generalPrecautions" to strList(),
            "otherNotes" to strList(),
            "unreadable" to strList()
        )

        val PROMPT = """
            The provided photo(s), PDF or text is a home exercise prescription for
            chronic pain, written by a doctor or physiotherapist. A photo may be
            printed or handwritten. A PDF may have several pages; read all of them.
            Several photos are pages of the same prescription, in order: read all
            of them and return one combined result. If the same item appears on
            two photos (e.g. overlapping shots), list it once. Read every part of
            every page: headers, tables, footnotes, margins, boxes, handwritten
            additions and the back of a page if shown.

            Your output is later turned into a daily pacing plan by another step
            that sees only your output, not the sheet. So capture every number,
            date and rule that decides how much to do on a given day and when the
            amount changes. If it is on the sheet and not in your output, the
            plan cannot use it.

            Rules:
            - Extract only what is on the sheet. Never add exercises, doses, rules or
              warnings that are not written there. Never fill a gap with a typical
              value.
            - Copy numbers, units, ranges and qualifiers exactly as written
              ("Up to 3 minutes", "10–15 seconds", "at least 2 points", "BID").
              Words like "up to", "at least", "no more than", "previous",
              "assigned", "actual", "consecutive" must be kept.
            - Copy every field as written even if it looks wrong (for example a phone
              number in a name field). Do not correct the sheet.
            - Copy dates exactly as written, including the year if shown. Do not
              reformat or guess a missing year.
            - Use an empty string or empty list for anything not on the sheet.
            - Use "unreadable" only for text that is on the sheet but you cannot
              read with confidence. Do not guess; describe what it is
              (e.g. "duration for days 8-14"). Do not list things that are simply
              missing from the sheet.
            - Do not return the patient's name, health number, date of birth or address.

            Fields:
            - programName, bodyArea (include left/right), clinicalContext,
              treatmentGoals, prescriber, clinicContact (phone, email, clinic
              name, hours), dateApproved.
            - startDate: the date the program starts (day 1), as written.
            - reviewPlan: when and under what conditions the program is reviewed,
              and what to do when the schedule ends or before the review.
            - exercises: one item per exercise. Do not merge exercises.
              - name: as written.
              - setup: starting position and posture.
              - steps: each movement step in order, one per item, without the
                step number.
              - pace: speed or timing of a cycle or repetition.
              - sets, reps: as written.
              - holdOrDuration: hold time per repetition and/or the session time,
                including the starting dose if this exercise has its own.
              - frequency: how often, with sessions per day and which days
                (e.g. "once daily", "twice a day", "every other day", "Mon/Wed/Fri").
              - intensityOrLoad: effort, weight or resistance, including the name
                of any scale used (e.g. "RPE 3/10", "light band", "2 kg").
              - precautions: limits and warnings for this exercise
                (e.g. "do not squeeze", "use a smaller movement if too sensitive").
            - schedule: one item per row of any progression table, phase or
              starting dose, in order. period = the day, week or date range exactly
              as written (e.g. "Days 1-7", "Week 2", "After review"); dose = the
              amount for that period with its qualifier; frequency = how often in
              that period; note = anything attached to the row (e.g. "ceiling, not
              a target"). Include the first/starting period. If the starting dose is
              only stated in the text, add it as its own row too.
            - limits: every hard maximum or minimum on dose, frequency or progression
              (e.g. "do not exceed 4 minutes", "minimum 1 minute",
              "increase no more than once in 7 days", "do not add extra sessions",
              "next-day pain must not rise more than 2 points").
            - adjustmentRules: every if/then rule that changes, holds or keeps the
              dose for the next session. This includes rules for:
                increasing (by how much, after how many sessions or days, and what
                must be true first);
                reducing (by how much, down to what minimum, and whether from the
                previous actual dose or the assigned dose);
                holding or repeating the dose;
                a missed session, a stopped or incomplete session, or missing
                pain scores;
                which pain is compared with which (e.g. "today's pain before
                exercise vs yesterday's", "next-morning pain vs baseline").
              label = the rule's letter or number if it has one; condition = the
              full "if" part; action = everything to do when it applies, including
              exceptions. Keep each rule complete; do not merge rules.
            - trackingInstructions: what to record, when and how
              (pain scale name and range, before/after/next day, completed time,
              reps done, missed sessions, symptoms, PEG or other questionnaires).
            - stopRules: every instruction to pause, stop or seek help. One trigger
              per item, copied in full. level must be exactly one of:
                "urgent_care" (seek urgent or emergency care),
                "stop_and_seek_assessment" (stop exercising and get assessed),
                "contact_team" (contact the clinician before continuing or progressing),
                "pause" (pause or ease off the session).
              If unsure between two levels, choose the more urgent one.
            - generalPrecautions: other warnings for the whole program, including
              conditions that mean "do not progress" (e.g. "do not increase if
              swelling is worse").
            - otherNotes: anything else on the sheet not captured above.
        """.trimIndent()
    }
}