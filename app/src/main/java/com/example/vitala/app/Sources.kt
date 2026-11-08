package com.example.vitala.app

import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.HealthLogRepository
import com.example.vitala.data.PegResponse
import com.example.vitala.pacing.adaptive.AdaptiveAction
import com.example.vitala.pacing.adaptive.AdaptiveEngine
import com.example.vitala.pacing.adaptive.ExerciseDay
import com.example.vitala.pacing.adaptive.ExerciseDose
import com.example.vitala.pacing.adaptive.ExerciseState
import com.example.vitala.pacing.adaptive.SessionRecord
import com.example.vitala.pacing.adaptive.SessionStatus
import com.example.vitala.pacing.integration.Evaluation
import com.example.vitala.pacing.integration.PreparedProgram
import com.google.gson.Gson
import java.time.LocalDate

/*
 * The three places the app reads and writes data, as interfaces. The real app uses Firestore for all three.
 * Demo mode swaps in the in-memory versions below, so the whole app can be used with no account and no internet.
 */

interface DataStore {
    val gson: Gson
    suspend fun loadRoot(uid: String): Pair<Profile?, Settings?>
    fun saveProfile(uid: String, p: Profile)
    fun saveSettings(uid: String, s: Settings)
    fun <T> put(uid: String, collection: String, id: String, value: T)
    fun remove(uid: String, collection: String, id: String)
    suspend fun <T> list(uid: String, collection: String, cls: Class<T>): List<T>
    suspend fun <T> get(uid: String, collection: String, id: String, cls: Class<T>): T?
    suspend fun rawAll(uid: String, collections: List<String>): Map<String, List<String>>
    suspend fun deleteEverything(uid: String)
}

interface HealthSource {
    suspend fun saveCheckIn(patientId: String, checkIn: DailyCheckIn)
    suspend fun checkIns(patientId: String, from: LocalDate, to: LocalDate): List<DailyCheckIn>
    suspend fun saveSession(patientId: String, session: ExerciseSession)
    suspend fun sessions(patientId: String, from: LocalDate, to: LocalDate): List<ExerciseSession>
    suspend fun savePeg(patientId: String, peg: PegResponse)
    suspend fun peg(patientId: String, from: LocalDate, to: LocalDate): List<PegResponse>
}

interface AdaptiveSource {
    suspend fun activate(patientId: String, prepared: PreparedProgram)
    suspend fun loadProgram(patientId: String, programId: String): PreparedProgram
    suspend fun saveCheckIn(patientId: String, programId: String, exerciseId: String, date: LocalDate, pain: Int, answers: Map<String, Boolean>)
    suspend fun currentState(patientId: String, programId: String, exerciseId: String): ExerciseState?
    suspend fun evaluate(patientId: String, programId: String, exerciseId: String, today: LocalDate): Evaluation
    suspend fun applyAssignment(patientId: String, evaluation: Evaluation): Boolean
    suspend fun recordSession(patientId: String, programId: String, exerciseId: String, date: LocalDate, actualValue: Double?, missed: Boolean)
}

/** Firestore-backed check-ins, sessions and PEG (the existing HealthLogRepository). */
class CloudHealth(private val repo: HealthLogRepository) : HealthSource {
    override suspend fun saveCheckIn(patientId: String, checkIn: DailyCheckIn) = repo.saveCheckIn(patientId, checkIn)
    override suspend fun checkIns(patientId: String, from: LocalDate, to: LocalDate) = repo.checkIns(patientId, from, to)
    override suspend fun saveSession(patientId: String, session: ExerciseSession) = repo.saveSession(patientId, session)
    override suspend fun sessions(patientId: String, from: LocalDate, to: LocalDate) = repo.sessions(patientId, from, to)
    override suspend fun savePeg(patientId: String, peg: PegResponse) = repo.savePeg(patientId, peg)
    override suspend fun peg(patientId: String, from: LocalDate, to: LocalDate) = repo.peg(patientId, from, to)
}

// ---------------------------------------------------------------------------------------------- in-memory (demo)

class LocalBackend {
    val store = LocalStore()
    val health = LocalHealth()
    val adaptive = LocalAdaptive()
}

/** Records are copied through JSON on the way in and out, so they behave like stored documents. */
class LocalStore : DataStore {
    override val gson = Gson()
    private var profileJson: String? = null
    private var settingsJson: String? = null
    private val data = mutableMapOf<String, MutableMap<String, String>>()

    override suspend fun loadRoot(uid: String): Pair<Profile?, Settings?> =
        (profileJson?.let { gson.fromJson(it, Profile::class.java) }) to (settingsJson?.let { gson.fromJson(it, Settings::class.java) })

    override fun saveProfile(uid: String, p: Profile) { profileJson = gson.toJson(p) }
    override fun saveSettings(uid: String, s: Settings) { settingsJson = gson.toJson(s) }
    override fun <T> put(uid: String, collection: String, id: String, value: T) {
        data.getOrPut(collection) { linkedMapOf() }[id] = gson.toJson(value)
    }
    override fun remove(uid: String, collection: String, id: String) { data[collection]?.remove(id) }
    override suspend fun <T> list(uid: String, collection: String, cls: Class<T>): List<T> =
        data[collection]?.values?.map { gson.fromJson(it, cls) }.orEmpty()
    override suspend fun <T> get(uid: String, collection: String, id: String, cls: Class<T>): T? =
        data[collection]?.get(id)?.let { gson.fromJson(it, cls) }
    override suspend fun rawAll(uid: String, collections: List<String>) =
        collections.associateWith { data[it]?.values?.toList().orEmpty() }
    override suspend fun deleteEverything(uid: String) { data.clear(); profileJson = null; settingsJson = null }
}

class LocalHealth : HealthSource {
    private val checkIns = sortedMapOf<LocalDate, DailyCheckIn>()
    private val sessions = linkedMapOf<String, ExerciseSession>()
    private val pegs = sortedMapOf<LocalDate, PegResponse>()
    override suspend fun saveCheckIn(patientId: String, checkIn: DailyCheckIn) { checkIns[checkIn.date] = checkIn }
    override suspend fun checkIns(patientId: String, from: LocalDate, to: LocalDate) = checkIns.values.filter { it.date in from..to }
    override suspend fun saveSession(patientId: String, session: ExerciseSession) { sessions[session.id] = session }
    override suspend fun sessions(patientId: String, from: LocalDate, to: LocalDate) =
        sessions.values.filter { it.localDate in from..to }.sortedBy { it.localDate }
    override suspend fun savePeg(patientId: String, peg: PegResponse) { pegs[peg.date] = peg }
    override suspend fun peg(patientId: String, from: LocalDate, to: LocalDate) = pegs.values.filter { it.date in from..to }
}

/**
 * The same bookkeeping as AdaptiveRepository (check-in revisions, one assignment per day, session recording),
 * kept in memory and running the real AdaptiveEngine for every recommendation.
 */
class LocalAdaptive : AdaptiveSource {
    private class Ex(
        val programId: String, val compiled: com.example.vitala.pacing.integration.CompiledExercise,
        var state: ExerciseState, var stateRevision: Long = 0, var inputRevision: Long = 0, var lastAssigned: LocalDate? = null,
        val reports: MutableMap<LocalDate, ExerciseDay> = sortedMapOf(), val assigned: MutableMap<LocalDate, ExerciseDose> = mutableMapOf()
    )

    private val programs = mutableMapOf<String, PreparedProgram>()
    private val exercises = mutableMapOf<String, Ex>()

    private fun ex(programId: String, exerciseId: String) =
        requireNotNull(exercises[exerciseId]?.takeIf { it.programId == programId }) { "Exercise not activated." }

    override suspend fun activate(patientId: String, prepared: PreparedProgram) {
        require(prepared.exercises.isNotEmpty())
        if (programs.containsKey(prepared.id)) return
        programs[prepared.id] = prepared
        prepared.exercises.forEach { c ->
            exercises[c.plan.exerciseId] = Ex(prepared.id, c, ExerciseState(c.plan.exerciseId, c.plan.startingDose, c.plan.programStart))
        }
    }

    /** Demo data: a program that is already part-way through, with its history. */
    fun seed(prepared: PreparedProgram, state: ExerciseState, reports: List<ExerciseDay>, assigned: Map<LocalDate, ExerciseDose>, lastAssigned: LocalDate) {
        programs[prepared.id] = prepared
        val c = prepared.exercises.first()
        exercises[c.plan.exerciseId] = Ex(prepared.id, c, state, stateRevision = reports.size.toLong(), inputRevision = reports.size * 2L, lastAssigned = lastAssigned).also {
            reports.forEach { r -> it.reports[r.date] = r }
            it.assigned.putAll(assigned)
        }
    }

    override suspend fun loadProgram(patientId: String, programId: String) =
        requireNotNull(programs[programId]) { "Program not found." }

    override suspend fun currentState(patientId: String, programId: String, exerciseId: String) = exercises[exerciseId]?.state

    override suspend fun saveCheckIn(patientId: String, programId: String, exerciseId: String, date: LocalDate, pain: Int, answers: Map<String, Boolean>) {
        require(pain in 0..10)
        val e = ex(programId, exerciseId)
        e.reports[date] = ExerciseDay(exerciseId, date, pain, e.reports[date]?.session, answers.toMap())
        e.inputRevision++
    }

    override suspend fun evaluate(patientId: String, programId: String, exerciseId: String, today: LocalDate): Evaluation {
        val e = ex(programId, exerciseId)
        val reports = e.reports.values.filter { it.date >= e.compiled.plan.programStart && it.date <= today }.sortedBy { it.date }
        val rec = AdaptiveEngine().recommend(e.compiled.plan, e.state, reports, today)
        return Evaluation(programId, e.stateRevision, e.inputRevision, rec)
    }

    override suspend fun applyAssignment(patientId: String, evaluation: Evaluation): Boolean {
        val r = evaluation.recommendation
        require(r.evaluatedFor == LocalDate.now()) { "Only today's assignment can be applied." }
        require(r.action in setOf(AdaptiveAction.START, AdaptiveAction.MAINTAIN, AdaptiveAction.INCREASE, AdaptiveAction.DECREASE)) { "This result does not provide an exercise assignment." }
        val next = requireNotNull(r.nextDose)
        val e = ex(evaluation.programId, r.exerciseId)
        if (e.lastAssigned == r.evaluatedFor) return false
        require(e.stateRevision == evaluation.stateRevision && e.inputRevision == evaluation.inputRevision) { "The inputs changed. Evaluate again before applying." }
        val changed = next != e.state.assignedDose
        e.state = e.state.copy(
            assignedDose = next,
            doseEffectiveFrom = if (changed) r.evaluatedFor else e.state.doseEffectiveFrom,
            lastIncreaseDate = if (r.action == AdaptiveAction.INCREASE) r.evaluatedFor else e.state.lastIncreaseDate
        )
        e.stateRevision++
        e.lastAssigned = r.evaluatedFor
        e.assigned[r.evaluatedFor] = next
        return true
    }

    override suspend fun recordSession(patientId: String, programId: String, exerciseId: String, date: LocalDate, actualValue: Double?, missed: Boolean) {
        val e = ex(programId, exerciseId)
        val report = requireNotNull(e.reports[date]) { "Save the check-in first." }
        require(report.session == null) { "A session is already recorded for this day." }
        val assigned = requireNotNull(e.assigned[date]) { "Apply the assignment first." }
        require(missed || (actualValue != null && actualValue.isFinite() && actualValue > 0.0))
        require(!missed || actualValue == null)
        val actual = if (missed) null else ExerciseDose(assigned.dimension, requireNotNull(actualValue))
        val status = when {
            missed -> SessionStatus.MISSED
            actual!!.value < assigned.value -> SessionStatus.PARTIAL
            else -> SessionStatus.COMPLETED
        }
        e.reports[date] = report.copy(session = SessionRecord(status, assigned, actual))
        e.inputRevision++
    }
}
