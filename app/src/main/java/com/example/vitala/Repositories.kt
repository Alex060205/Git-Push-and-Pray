package com.example.vitala

import java.time.LocalDate

// ---------- Interfaces (screens and logic only talk to these) ----------

interface PatientRepository {
    fun getPatient(): Patient?
    fun savePatient(patient: Patient)
    fun getActivePlan(): TreatmentPlan?
    fun getAllPlans(): List<TreatmentPlan>

    /** Adds a new plan from the doctor, makes it active and deactivates the old one. */
    fun addPlan(plan: TreatmentPlan)
}

interface CheckInRepository {
    /** Replaces any check-in already saved for the same date. */
    fun save(checkIn: DailyCheckIn)
    fun getFor(date: LocalDate): DailyCheckIn?
    fun getRange(from: LocalDate, to: LocalDate): List<DailyCheckIn>
    fun getAll(): List<DailyCheckIn>
}

interface ActivityLogRepository {
    /** Inserts, or updates if a session with the same id exists. */
    fun saveSession(session: ExerciseSession)
    fun getSessionsFor(date: LocalDate): List<ExerciseSession>
    fun getSessions(from: LocalDate, to: LocalDate): List<ExerciseSession>

    /** Inserts, or updates if a dose with the same id exists. */
    fun saveDose(log: MedicationLog)
    fun getDosesFor(date: LocalDate): List<MedicationLog>
    fun getDoses(from: LocalDate, to: LocalDate): List<MedicationLog>
}

// ---------- In-memory versions (data is lost when the app closes) ----------

class InMemoryPatientRepository : PatientRepository {
    private var patient: Patient? = null
    private val plans = mutableListOf<TreatmentPlan>()

    override fun getPatient(): Patient? = patient

    override fun savePatient(patient: Patient) {
        this.patient = patient
    }

    override fun getActivePlan(): TreatmentPlan? = plans.firstOrNull { it.isActive }

    override fun getAllPlans(): List<TreatmentPlan> = plans.sortedBy { it.version }

    override fun addPlan(plan: TreatmentPlan) {
        val nextVersion = (plans.maxOfOrNull { it.version } ?: 0) + 1
        for (i in plans.indices) plans[i] = plans[i].copy(isActive = false)
        val newPlan = plan.copy(version = nextVersion, isActive = true)
        plans += newPlan
        patient = patient?.copy(activePlanId = newPlan.id)
    }
}

class InMemoryCheckInRepository : CheckInRepository {
    private val checkIns = mutableMapOf<LocalDate, DailyCheckIn>()

    override fun save(checkIn: DailyCheckIn) {
        checkIns[checkIn.date] = checkIn
    }

    override fun getFor(date: LocalDate): DailyCheckIn? = checkIns[date]

    override fun getRange(from: LocalDate, to: LocalDate): List<DailyCheckIn> =
        checkIns.values.filter { it.date in from..to }.sortedBy { it.date }

    override fun getAll(): List<DailyCheckIn> = checkIns.values.sortedBy { it.date }
}

class InMemoryActivityLogRepository : ActivityLogRepository {
    private val sessions = linkedMapOf<String, ExerciseSession>()
    private val doses = linkedMapOf<String, MedicationLog>()

    override fun saveSession(session: ExerciseSession) {
        sessions[session.id] = session
    }

    override fun getSessionsFor(date: LocalDate): List<ExerciseSession> =
        sessions.values.filter { it.date == date }.sortedBy { it.scheduledTime }

    override fun getSessions(from: LocalDate, to: LocalDate): List<ExerciseSession> =
        sessions.values.filter { it.date in from..to }
            .sortedWith(compareBy({ it.date }, { it.scheduledTime }))

    override fun saveDose(log: MedicationLog) {
        doses[log.id] = log
    }

    override fun getDosesFor(date: LocalDate): List<MedicationLog> =
        doses.values.filter { it.date == date }.sortedBy { it.scheduledTime }

    override fun getDoses(from: LocalDate, to: LocalDate): List<MedicationLog> =
        doses.values.filter { it.date in from..to }
            .sortedWith(compareBy({ it.date }, { it.scheduledTime }))
}