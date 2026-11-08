package com.example.vitala.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitala.Recognition.ExercisePrescription as Scanned
import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.ExerciseItem
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.HealthLogRepository
import com.example.vitala.data.Patient
import com.example.vitala.data.PegResponse
import com.example.vitala.data.PrescribedExercise
import com.example.vitala.data.Recovery
import com.example.vitala.data.RecoveryStatus
import com.example.vitala.data.RecoveryTime
import com.example.vitala.data.StopReason
import com.example.vitala.data.SymptomResponse
import com.example.vitala.data.PainFeeling
import com.example.vitala.pacing.adaptive.AdaptiveAction
import com.example.vitala.pacing.adaptive.AdaptiveRecommendation
import com.example.vitala.pacing.adaptive.DoseDimension
import com.example.vitala.pacing.adaptive.ExerciseDose
import com.example.vitala.pacing.adaptive.ExerciseState
import com.example.vitala.pacing.integration.AdaptiveRepository
import com.example.vitala.pacing.integration.CompiledExercise
import com.example.vitala.pacing.integration.Evaluation
import com.example.vitala.pacing.integration.PreparedProgram
import com.example.vitala.report.ReportData
import com.example.vitala.report.ReportPdf
import java.time.Instant
import com.example.vitala.report.ReportSaver
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** An exercise that applies today, with everything the screens need. */
data class TodayItem(
    val plan: PlanVersionRec,
    val compiled: CompiledExercise,
    val state: ExerciseState?,
    val programDay: Int,
    val totalDays: Int,
    val ceilingToday: Double?,
    val notStarted: Boolean,
    val ended: Boolean
) {
    val exerciseId get() = compiled.plan.exerciseId
    val name get() = compiled.plan.activity
    val dimension get() = compiled.plan.startingDose.dimension
    /** What the plan allows today before the pain rating: the assigned dose, capped by today's ceiling. */
    val upTo: Double? get() = state?.assignedDose?.value?.let { v -> ceilingToday?.let { minOf(v, it) } ?: v }
}

data class CheckInDraft(
    val pain: Int? = null,
    val fatigue: Int? = null,
    val interference: Int? = null,
    val recovery: RecoveryStatus? = null,
    val recoveryTime: RecoveryTime? = null,
    val context: Set<ContextType> = emptySet(),
    val note: String = ""
)

data class LogDraft(
    val actuals: Map<String, Double> = emptyMap(),
    val notDone: Boolean = false,
    val symptom: SymptomResponse? = null,
    val newSymptoms: Set<String> = emptySet(),
    val stopReason: StopReason? = null
)

data class AppState(
    val ready: Boolean = false,
    val uid: String? = null,
    val email: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,

    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val conditions: List<ConditionRec> = emptyList(),
    val plans: List<PlanVersionRec> = emptyList(),
    val programs: Map<String, PreparedProgram> = emptyMap(),
    val states: Map<String, ExerciseState> = emptyMap(),
    val meds: List<MedicationRec> = emptyList(),
    val medEvents: List<MedEvent> = emptyList(),
    val contexts: List<ContextEventRec> = emptyList(),
    val recs: List<RecommendationRec> = emptyList(),
    val checkIns: List<DailyCheckIn> = emptyList(),
    val sessions: List<ExerciseSession> = emptyList(),
    val pegs: List<PegResponse> = emptyList(),
    val day: DayRec = DayRec(),
    val pendingPain: Int? = null,
    val draft: CheckInDraft = CheckInDraft(),
    val log: LogDraft = LogDraft(),
    val weeklyDraft: List<Int?> = listOf(null, null, null),
    val timeSuggestion: TimeSuggestion? = null,
    val calendarNote: String? = null,
    val loadFailed: Boolean = false,
    val demo: Boolean = false,

    val tab: Tab = Tab.HOME,
    val nav: List<Screen> = listOf(Screen.Auth)
) {
    val screen: Screen get() = nav.lastOrNull() ?: Screen.Auth
    val activeConditions get() = conditions.filter { !it.archived }
    fun currentPlan(conditionId: String) = plans.firstOrNull { it.conditionId == conditionId && it.current }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val auth = FirebaseAuth.getInstance()
    private val cloudStore = UserStore()
    private val cloudHealth by lazy { CloudHealth(HealthLogRepository()) }
    private val cloudAdaptive by lazy { AdaptiveRepository() }

    /** Non-null in demo mode: the same three data sources, kept in memory (no account, no internet). */
    private var local: LocalBackend? = null
    private val store: DataStore get() = local?.store ?: cloudStore
    private val health: HealthSource get() = local?.health ?: cloudHealth
    private val adaptive: AdaptiveSource get() = local?.adaptive ?: cloudAdaptive
    private val context get() = getApplication<Application>()

    private val _s = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _s.asStateFlow()
    private val s get() = _s.value
    private val uid get() = requireNotNull(s.uid) { "Please sign in first." }
    private val today get() = LocalDate.now()

    private var demoPending = false

    /** Engine evaluations from today's pre-session answers; re-created on demand after a restart. */
    private val evals = mutableMapOf<String, Evaluation>()

    init {
        auth.addAuthStateListener { fb ->
            val user = fb.currentUser
            if (user == null && local != null) return@addAuthStateListener      // demo mode has no Firebase user
            if (user == null) {
                evals.clear()
                _s.value = AppState(ready = true, error = s.error)
            } else if (s.uid != user.uid && !demoPending) {
                _s.update { it.copy(uid = user.uid, email = user.email.orEmpty(), busy = true) }
                viewModelScope.launch { loadAll(user.uid) }
            }
        }
    }

    // ------------------------------------------------------------------ small helpers

    private fun edit(f: AppState.() -> AppState) = _s.update { it.f() }

    fun clearMessage() = edit { copy(message = null, error = null) }
    private fun fail(e: Throwable, fallback: String = "Something went wrong. Please try again.") {
        if (e is CancellationException) throw e
        android.util.Log.e("Vitala", fallback, e)
        edit { copy(error = e.message?.takeIf { it.length < 160 } ?: fallback, busy = false) }
    }

    private fun work(block: suspend () -> Unit) {
        if (s.busy) return
        edit { copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (e: Throwable) { fail(e) } finally { edit { copy(busy = false) } }
        }
    }

    private fun background(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure { android.util.Log.w("Vitala", "Background save failed", it) } }
    }

    // ------------------------------------------------------------------ navigation

    fun go(screen: Screen) = edit { copy(nav = nav + screen, error = null, message = null) }
    fun back() = edit { if (nav.size > 1) copy(nav = nav.dropLast(1), error = null) else this }
    fun canGoBack() = s.nav.size > 1
    /** Show the outcome of the questions without keeping the question screens in the back stack. */
    fun showResult(screen: Screen) = edit { copy(nav = listOf(rootOf(Tab.HOME), screen), tab = Tab.HOME) }
    fun clearToday() { persistDay(DayRec()); edit { copy(pendingPain = null) } }
    fun replaceTop(screen: Screen) = edit { copy(nav = nav.dropLast(1) + screen, error = null) }
    fun resetTo(vararg screens: Screen) = edit { copy(nav = screens.toList(), error = null, message = null) }
    fun selectTab(t: Tab) = edit { copy(tab = t, nav = listOf(rootOf(t)), error = null, message = null) }
    fun toHome() = edit { copy(tab = Tab.HOME, nav = listOf(rootOf(Tab.HOME)), error = null) }
    private fun rootOf(t: Tab): Screen = Screen.TabRoot(t)

    /** True when the screen on top is a tab root (tab bar visible). */
    fun isTabRoot(): Boolean = s.nav.size <= 1 && s.profile.onboarded && s.uid != null

    fun handleOpen(open: String?) {
        if (open.isNullOrBlank() || s.uid == null || !s.profile.onboarded) return
        when {
            open.startsWith("med:") -> {
                val parts = open.split(":", limit = 3)
                if (parts.size == 3) resetToTab(Tab.HOME, Screen.MedicineReminder(parts[1], parts[2]))
            }
            open.startsWith("exercise:") -> resetToTab(Tab.HOME, Screen.ExerciseReminder(open.removePrefix("exercise:")))
            open == "checkin" -> resetToTab(Tab.HOME, Screen.CheckIn(0))
            open == "weekly" -> resetToTab(Tab.HOME, Screen.Weekly(0))
        }
    }

    private fun resetToTab(tab: Tab, screen: Screen) = edit { copy(tab = tab, nav = listOf(rootOf(tab), screen)) }

    // ------------------------------------------------------------------ auth

    fun signIn(email: String, password: String) = authCall { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    fun signUp(email: String, password: String) = authCall { auth.createUserWithEmailAndPassword(email.trim(), password).await() }

    fun resetPassword(email: String) {
        if (email.isBlank()) { edit { copy(error = "Type your email first, then tap this again.") }; return }
        work {
            auth.sendPasswordResetEmail(email.trim()).await()
            edit { copy(message = "We sent a reset link to ${email.trim()}.") }
        }
    }

    private fun authCall(block: suspend () -> Unit) {
        if (s.busy) return
        edit { copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (e: Throwable) {
                if (e is CancellationException) throw e
                edit { copy(busy = false, error = friendlyAuth(e)) }
            }
            // On success the auth listener loads the data and clears busy.
        }
    }

    private fun friendlyAuth(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            "password is invalid" in m || "credential is incorrect" in m || "INVALID_LOGIN" in m || "supplied auth credential" in m ->
                "That email or password isn't right."
            "already in use" in m -> "There is already an account with that email. Try signing in."
            "at least 6" in m || "WEAK_PASSWORD" in m -> "Please choose a password with at least 6 characters."
            "badly formatted" in m -> "That email doesn't look right."
            "network" in m.lowercase() -> "No connection. Check your internet and try again."
            "CONFIGURATION_NOT_FOUND" in m || "OPERATION_NOT_ALLOWED" in m ->
                "Email sign-in is not switched on in Firebase yet. See the setup list."
            else -> "We couldn't sign you in. Please try again."
        }
    }

    fun signOut() {
        if (local != null) { leaveDemo(); return }
        Reminders.clear(context)
        auth.signOut()
    }

    // ------------------------------------------------------------------ loading

    private suspend fun loadAll(uid: String) {
        try {
            // If this read fails we must NOT treat the person as brand new (that would restart onboarding and
            // could overwrite their profile), so the error goes to the retry screen below.
            val (p, st) = store.loadRoot(uid)
            val profile = p ?: Profile()
            val settings = st ?: Settings()
            val from = today.minusDays(120)
            coroutineScope {
                val conditions = async { runCatching { store.list(uid, "conditions", ConditionRec::class.java) }.getOrDefault(emptyList()) }
                val plans = async { runCatching { store.list(uid, "plans", PlanVersionRec::class.java) }.getOrDefault(emptyList()) }
                val meds = async { runCatching { store.list(uid, "medications", MedicationRec::class.java) }.getOrDefault(emptyList()) }
                val medEvents = async { runCatching { store.list(uid, "medEvents", MedEvent::class.java) }.getOrDefault(emptyList()) }
                val contexts = async { runCatching { store.list(uid, "contexts", ContextEventRec::class.java) }.getOrDefault(emptyList()) }
                val recs = async { runCatching { store.list(uid, "recommendations", RecommendationRec::class.java) }.getOrDefault(emptyList()) }
                val checkIns = async { runCatching { health.checkIns(uid, from, today) }.getOrDefault(emptyList()) }
                val sessions = async { runCatching { health.sessions(uid, from, today) }.getOrDefault(emptyList()) }
                val pegs = async { runCatching { health.peg(uid, from, today) }.getOrDefault(emptyList()) }
                val day = async { runCatching { store.get(uid, "days", today.toString(), DayRec::class.java) }.getOrNull() }

                val planList = plans.await()
                val programs = mutableMapOf<String, PreparedProgram>()
                val states = mutableMapOf<String, ExerciseState>()
                val currentIds = planList.filter { it.current }.map { it.id }
                currentIds.map { id ->
                    async {
                        runCatching { adaptive.loadProgram(uid, id) }.getOrNull()?.let { prog ->
                            programs[id] = prog
                            prog.exercises.forEach { ex ->
                                runCatching { adaptive.currentState(uid, id, ex.plan.exerciseId) }.getOrNull()
                                    ?.let { states[ex.plan.exerciseId] = it }
                            }
                        }
                    }
                }.forEach { it.await() }

                val prof = profile
                _s.update {
                    it.copy(
                        ready = true, busy = false, uid = uid,
                        profile = prof, settings = settings,
                        conditions = conditions.await(), plans = planList, programs = programs, states = states,
                        meds = meds.await(), medEvents = medEvents.await(), contexts = contexts.await(), recs = recs.await(),
                        checkIns = checkIns.await(), sessions = sessions.await(), pegs = pegs.await(),
                        day = day.await() ?: DayRec(),
                        tab = Tab.HOME
                    )
                }
            }
            enterAfterLoad()
            rescheduleReminders()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            edit { copy(ready = true, busy = false, loadFailed = true, error = "We couldn't load your data. Check your connection.") }
        }
    }

    /** Signed in already, but loading failed (for example no connection). */
    fun retryLoad() {
        val id = s.uid ?: return
        edit { copy(busy = true, loadFailed = false, error = null) }
        viewModelScope.launch { loadAll(id) }
    }

    /**
     * Demo mode: Maria Lutke (the demo user from the brief), kept in memory on this phone. No account, no internet and
     * nothing is sent to Firebase. Everything in the app works on this data; it resets when you sign out or restart.
     */
    fun useDemoUser() {
        if (s.busy) return
        edit { copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val backend = LocalBackend()
                DemoSeeder(backend).seed(DemoSeeder.UID, today)
                local = backend
                evals.clear()
                _s.update { it.copy(uid = DemoSeeder.UID, email = "Demo user · stays on this phone", demo = true, busy = true) }
                loadAll(DemoSeeder.UID)
            } catch (e: Throwable) {
                local = null
                if (e is CancellationException) throw e
                android.util.Log.e("Vitala", "Demo user failed", e)
                edit { copy(busy = false, error = "Demo user failed: ${e.message}") }
            }
        }
    }

    private fun leaveDemo() {
        local = null
        evals.clear()
        Reminders.clear(context)
        _s.value = AppState(ready = true)
    }

    private fun enterAfterLoad() {
        val p = s.profile
        if (p.onboarded) { edit { copy(nav = listOf(rootOf(Tab.HOME))) }; return }
        resumeOnboarding()
    }

    // ------------------------------------------------------------------ onboarding

    fun resumeOnboarding() {
        val p = s.profile
        val cond = s.conditions.lastOrNull()
        val screen: Screen = when (p.onboardingStep) {
            0 -> Screen.AboutYou
            1 -> Screen.SetupDay
            2 -> Screen.AddCondition(true)
            3 -> if (cond != null) Screen.AddPlan(cond.id, true) else Screen.AddCondition(true)
            4 -> Screen.ReminderTimes(onboarding = true)
            5 -> Screen.OnboardMedicines
            else -> Screen.OnboardCalendar
        }
        edit { copy(nav = listOf(screen)) }
    }

    fun setOnboardingStep(step: Int, then: Screen? = null) {
        val p = s.profile.copy(onboardingStep = step)
        edit { copy(profile = p) }
        store.saveProfile(uid, p)
        if (step >= 7) { edit { copy(tab = Tab.HOME, nav = listOf(rootOf(Tab.HOME))) } }
        else if (then != null) edit { copy(nav = listOf(then)) } else resumeOnboarding()
    }

    fun saveAboutYou(first: String, last: String, provider: String, onboarding: Boolean) {
        val p = s.profile.copy(
            firstName = first.trim(), lastName = last.trim(), medicalProvider = provider.trim(),
            onboardingStep = if (onboarding) maxOf(1, s.profile.onboardingStep) else s.profile.onboardingStep
        )
        edit { copy(profile = p) }
        store.saveProfile(uid, p)
        if (onboarding) resumeOnboarding() else back()
    }

    fun saveSettings(f: Settings.() -> Settings) {
        val st = s.settings.f()
        edit { copy(settings = st) }
        store.saveSettings(uid, st)
        rescheduleReminders()
    }

    fun saveHealth(heightCm: Double?, weightKg: Double?) {
        val p = s.profile.copy(
            heightCm = heightCm ?: s.profile.heightCm, weightKg = weightKg ?: s.profile.weightKg,
            healthUpdated = today.toString()
        )
        edit { copy(profile = p) }
        store.saveProfile(uid, p)
    }

    // ------------------------------------------------------------------ conditions and plans

    fun addCondition(name: String, diagnosis: String, onboarding: Boolean) {
        val c = ConditionRec(name = name.trim(), diagnosis = diagnosis.trim())
        edit { copy(conditions = conditions + c) }
        store.put(uid, "conditions", c.id, c)
        if (onboarding) {
            val p = s.profile.copy(onboardingStep = 3); edit { copy(profile = p) }; store.saveProfile(uid, p)
        }
        replaceTop(Screen.AddPlan(c.id, onboarding))
    }

    /** "Do this later": the condition is saved without a plan; the plan can be added from the Plan tab. */
    fun addConditionOnly(name: String, diagnosis: String, onboarding: Boolean) {
        val c = ConditionRec(name = name.trim(), diagnosis = diagnosis.trim())
        edit { copy(conditions = conditions + c) }
        store.put(uid, "conditions", c.id, c)
        if (onboarding) setOnboardingStep(5, Screen.OnboardMedicines) else back()
    }

    fun updateCondition(c: ConditionRec) {
        edit { copy(conditions = conditions.map { if (it.id == c.id) c else it }) }
        store.put(uid, "conditions", c.id, c)
        rescheduleReminders()
    }

    /**
     * Saves the reviewed plan as a new version (PL-6): the engine data is created, the previous
     * version stays read-only, and the original photos are kept with the version (PL-8).
     */
    fun savePlan(
        conditionId: String, rx: Scanned, prepared: PreparedProgram, source: String,
        doctor: String, imagePaths: List<String>, onboarding: Boolean, onSaved: () -> Unit
    ) = work {
        val items = PlanReview.build(rx, prepared)
        adaptive.activate(uid, prepared)

        val dir = File(context.filesDir, "plans/${prepared.id}").apply { mkdirs() }
        val kept = withContext(Dispatchers.IO) {
            imagePaths.mapIndexedNotNull { i, path ->
                val src = File(path)
                if (!src.exists()) null else File(dir, "page_${i + 1}.${src.extension.ifBlank { "jpg" }}").also { src.copyTo(it, overwrite = true) }.absolutePath
            }
        }

        val previous = s.plans.filter { it.conditionId == conditionId }
        val version = (previous.maxOfOrNull { it.version } ?: 0) + 1
        val rec = PlanVersionRec(
            id = prepared.id, conditionId = conditionId, version = version, doctor = doctor.trim(),
            planDate = rx.dateApproved, startDate = rx.startDate, source = source, imagePaths = kept,
            current = true, items = items
        )
        val retired = previous.filter { it.current }.map { it.copy(current = false, replacedOn = today.toString()) }
        retired.forEach { store.put(uid, "plans", it.id, it) }
        store.put(uid, "plans", rec.id, rec)

        val states = s.states.toMutableMap()
        prepared.exercises.forEach { ex ->
            states[ex.plan.exerciseId] = ExerciseState(ex.plan.exerciseId, ex.plan.startingDose, ex.plan.programStart)
        }
        // Engine state restarts on a new plan; the old program's exercise states are dropped from view.
        val oldIds = previous.filter { it.current }.mapNotNull { s.programs[it.id] }.flatMap { it.exercises }.map { it.plan.exerciseId }
        oldIds.forEach { states.remove(it) }

        // Times: prefilled from the usual time, or the times already set for exercises with the same name (PL-13).
        val existing = s.settings.exerciseTimes
        val times = prepared.exercises.map { ex ->
            val name = ex.plan.activity
            val old = existing.firstOrNull { it.exerciseName.equals(name, true) }
            ExerciseTime(name, ex.plan.exerciseId, old?.time ?: s.settings.usualExerciseTime, old?.leadMinutes ?: s.settings.leadMinutes)
        }
        val keepOthers = existing.filter { e -> s.plans.none { p -> p.current && p.conditionId != conditionId && s.programs[p.id]?.exercises?.any { it.plan.exerciseId == e.exerciseId } == true } }
            .filter { e -> s.activeConditions.any { c -> c.id != conditionId && s.currentPlan(c.id)?.let { p -> s.programs[p.id]?.exercises?.any { it.plan.exerciseId == e.exerciseId } } == true } }
        val newSettings = s.settings.copy(exerciseTimes = keepOthers + times)
        store.saveSettings(uid, newSettings)

        var prof = s.profile
        if (onboarding) { prof = prof.copy(onboardingStep = 4); store.saveProfile(uid, prof) }

        edit {
            copy(
                plans = plans.filter { it.conditionId != conditionId } + retired + rec,
                programs = programs + (prepared.id to prepared),
                states = states, settings = newSettings, profile = prof,
                day = DayRec(), message = "Plan saved"
            )
        }
        evals.clear()
        rescheduleReminders()
        onSaved()
        // PL-13 / PL-14: reminder times come straight after saving.
        resetToRemindersAfterPlan(onboarding)
    }

    private fun resetToRemindersAfterPlan(onboarding: Boolean) {
        val target = Screen.ReminderTimes(onboarding = onboarding, fromSave = true)
        if (onboarding) edit { copy(nav = listOf(target)) }
        else edit { copy(tab = Tab.PLAN, nav = listOf(rootOf(Tab.PLAN), target)) }
    }

    fun ensureProgram(programId: String) {
        if (s.programs.containsKey(programId) || s.uid == null) return
        background {
            val p = adaptive.loadProgram(uid, programId)
            edit { copy(programs = programs + (programId to p)) }
        }
    }

    // ------------------------------------------------------------------ today

    fun todayItems(): List<TodayItem> {
        val out = mutableListOf<TodayItem>()
        for (c in s.activeConditions) {
            val plan = s.currentPlan(c.id) ?: continue
            val prog = s.programs[plan.id] ?: continue
            for (ex in prog.exercises) {
                val p = ex.plan
                val day = ChronoUnit.DAYS.between(p.programStart, today).toInt() + 1
                val total = p.ceilings.maxOf { it.lastDay }
                val ceiling = p.ceilings.firstOrNull { day in it.firstDay..it.lastDay }?.maximum
                out += TodayItem(
                    plan, ex, s.states[p.exerciseId], day, total, ceiling,
                    notStarted = day < 1, ended = day > total
                )
            }
        }
        return out
    }

    fun timeFor(item: TodayItem): String =
        s.settings.dayTimes[today.toString()]?.get(item.exerciseId)
            ?: s.settings.exerciseTimes.firstOrNull { it.exerciseId == item.exerciseId }?.time
            ?: s.settings.exerciseTimes.firstOrNull { it.exerciseName.equals(item.name, true) }?.time
            ?: s.settings.usualExerciseTime

    /** Exercises that share a time form one session (ER-18). */
    fun sessionsToday(): List<Pair<String, List<TodayItem>>> =
        todayItems().groupBy { timeFor(it) }.toSortedMap().map { it.key to it.value }

    fun safetyQuestions(): List<String> =
        todayItems().flatMap { it.compiled.questions.values }.distinct()

    fun snoozeMedicine(medId: String, time: String) {
        logMed(medId, time, MedAction.SNOOZED)
        Reminders.snoozeKey(context, "med-$medId-$time")
    }
    fun snoozeExercise(time: String) {
        val key = s.settings.exerciseTimes.firstOrNull { it.time == time }?.let { "ex-${it.exerciseId.ifBlank { it.exerciseName }}" } ?: return
        Reminders.snoozeKey(context, key)
    }

    fun setPendingPain(v: Int) = edit { copy(pendingPain = v) }

    private fun clinicOf(item: TodayItem): String {
        val src = s.programs[item.plan.id]?.source ?: return ""
        return PlanReview.contact(src)
    }

    /**
     * Saves today's pain and safety answers for every exercise, asks the engine, and shows the result.
     * A missing answer stays missing: unticked boxes here mean the person saw the question and said no.
     */
    fun submitPreSession(pain: Int, ticked: Set<String>) = work {
        val items = todayItems().filter { !it.notStarted }
        val yesterday = runCatching { store.get(uid, "days", today.minusDays(1).toString(), DayRec::class.java) }.getOrNull()
        val result = mutableListOf<DayExercise>()
        for (item in items) {
            val answers = item.compiled.questions.mapValues { (_, text) -> text in ticked }
            adaptive.saveCheckIn(uid, item.plan.id, item.exerciseId, today, pain, answers)
            val ev = adaptive.evaluate(uid, item.plan.id, item.exerciseId, today)
            evals[item.exerciseId] = ev
            result += toDayExercise(item, ev.recommendation, pain, yesterday?.pain)
        }
        if (items.isEmpty()) {
            val notStarted = todayItems().firstOrNull()
            result += DayExercise(
                name = notStarted?.name.orEmpty(), action = "NOT_STARTED",
                reason = "Your plan starts on ${notStarted?.compiled?.plan?.programStart ?: "a later date"}."
            )
        }
        val suggested = sessionsToday().firstOrNull()?.first ?: s.settings.usualExerciseTime
        val day = DayRec(date = today.toString(), pain = pain, exercises = result, suggestedTime = suggested)
        edit { copy(day = day, pendingPain = pain) }
        store.put(uid, "days", day.date, day)
        val level = if (day.blocked) "HOLD" else if (result.any { it.action == "DECREASE" }) Level.SHORTER.name else Level.PLANNED.name
        store.put(uid, "recommendations", day.date, RecommendationRec(
            date = day.date, level = level, reason = result.firstOrNull()?.reason.orEmpty(),
            ruleIds = result.map { it.ruleId }, programIds = result.map { it.programId }.distinct()
        ))
        edit { copy(recs = recs.filter { it.date != day.date } + RecommendationRec(day.date, level, result.firstOrNull()?.reason.orEmpty(), result.map { it.ruleId }, result.map { it.programId }.distinct())) }
        showResult(if (day.blocked) Screen.SafetyBlock else Screen.SessionToday)
    }

    private fun toDayExercise(item: TodayItem, rec: AdaptiveRecommendation, pain: Int, yesterdayPain: Int?): DayExercise {
        val p = item.compiled.plan
        val dim = p.startingDose.dimension
        val st = item.state
        val minDays = p.progression.minimumDaysBetweenIncreases
        val noSooner = st?.lastIncreaseDate?.plusDays(minDays.toLong())
        val dose = rec.nextDose?.value
        val doseText = dose?.let { Dose.fmt(dim, it) } ?: ""
        val painDelta = yesterdayPain?.let { pain - it }
        val reason = when (rec.ruleId) {
            "STARTING_DOSE" -> "This is the amount your plan starts with."
            "PAIN_INCREASE_REDUCTION" ->
                "Your pain before exercise went up${painDelta?.let { " by $it points" } ?: ""}, so your plan shortens today to $doseText. " +
                        "This does not mean yesterday's exercise caused it."
            "MINIMUM_DOSE_REACHED" -> "Your pain went up, but your plan does not go below its smallest amount. ${rec.instructions}".trim()
            "REDUCTION_ALREADY_IN_EFFECT" -> "Your time stays at $doseText, the lower amount already in use."
            "PARTIAL_SESSION" -> "Yesterday's session was shorter than planned, so your time stays at $doseText."
            "INCOMPLETE_PREVIOUS_COMPARISON" ->
                "We don't have yesterday's pain and time, so your time stays at $doseText. A missing answer is never counted as zero."
            "PROGRESSION_BLOCKED" -> "Something on your plan's list is present today, so your time stays at $doseText."
            "INCREASE_INTERVAL", "INSUFFICIENT_CURRENT_DOSE_HISTORY", "INSUFFICIENT_CONSECUTIVE_EVIDENCE",
            "FOLLOWING_DAY_RESPONSE", "HISTORICAL_SYMPTOM_EVIDENCE" ->
                "Your pain before exercise is ${painNote(pain, yesterdayPain)}, so your time stays at $doseText until your plan's steady-sessions rule is met."
            "WEEKLY_CEILING" -> "Your pain today ($pain) ${yesterdayPain?.let { if (pain == it) "is the same as yesterday ($it)" else "is $pain" } ?: "is $pain"}, " +
                    "so your time stays at $doseText. This is the most your plan allows this week."
            "PROGRESSION" -> "Your recent sessions went steadily, and your plan allows $doseText today."
            else -> rec.reason
        }
        val note = if (rec.action == AdaptiveAction.MAINTAIN || rec.action == AdaptiveAction.START) {
            noSooner?.let { "Your plan allows an increase no sooner than ${it.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)} ${it.dayOfMonth}, if your pain stays steady." }.orEmpty()
        } else ""
        return DayExercise(
            programId = item.plan.id, exerciseId = item.exerciseId, name = item.name, dimension = dim.name,
            action = rec.action.name, doseValue = dose, minimumValue = p.reduction.minimum,
            reason = reason, instructions = rec.instructions, ruleId = rec.ruleId,
            clinicContact = clinicOf(item), note = note
        )
    }

    private fun painNote(now: Int, before: Int?) = when {
        before == null -> "$now"
        now == before -> "$now, the same as yesterday ($before)"
        else -> "$now (yesterday $before)"
    }

    /** Progression is a question, never automatic (ER-5): "Not today" holds the current time. */
    fun declineIncrease() {
        val items = todayItems()
        val d = s.day
        val newList = d.exercises.map { ex ->
            if (ex.action != "INCREASE") ex else {
                val st = items.firstOrNull { it.exerciseId == ex.exerciseId }?.state
                ex.copy(
                    action = "MAINTAIN", ruleId = "PROGRESSION_DECLINED",
                    doseValue = st?.assignedDose?.value ?: ex.doseValue,
                    reason = "You chose to stay at ${st?.assignedDose?.value?.let { Dose.fmt(ex.dimension, it) } ?: "today's amount"}. Your plan will offer the increase again.",
                    note = ""
                )
            }
        }
        persistDay(d.copy(exercises = newList))
    }

    fun chooseLevel(level: Level) {
        persistDay(s.day.copy(levelChosen = level.name))
    }

    private fun persistDay(d: DayRec) {
        edit { copy(day = d) }
        background { store.put(uid, "days", d.date, d) }
    }

    /** Dose to aim for under a level, per exercise. */
    fun targetFor(ex: DayExercise, level: Level): Double? = when (level) {
        Level.PLANNED -> ex.doseValue
        Level.SHORTER -> ex.minimumValue?.let { m -> ex.doseValue?.let { minOf(it, m) } ?: m }
        Level.REST -> 0.0
    }

    /** The engine's dose for today is always the "planned" level; Shorter and Rest are the person's own choices (overrides). */
    fun suggestedLevel(): Level = Level.PLANNED

    /**
     * Records the engine's assignment for today so the session can be logged (needed by the engine before
     * recordSession). Called when the person starts, or chooses Rest.
     */
    fun startSession(level: Level, onReady: () -> Unit) = work {
        val d = s.day
        if (!d.applied) {
            for (ex in d.exercises.filter { it.exerciseId.isNotBlank() }) {
                val item = todayItems().first { it.exerciseId == ex.exerciseId }
                val base = evals[ex.exerciseId] ?: adaptive.evaluate(uid, ex.programId, ex.exerciseId, today)
                val dim = DoseDimension.valueOf(ex.dimension)
                val rec = base.recommendation.copy(
                    action = if (ex.action == "INCREASE") AdaptiveAction.INCREASE else if (ex.action == "DECREASE") AdaptiveAction.DECREASE
                    else if (ex.action == "START") AdaptiveAction.START else AdaptiveAction.MAINTAIN,
                    nextDose = ExerciseDose(dim, requireNotNull(ex.doseValue))
                )
                adaptive.applyAssignment(uid, base.copy(recommendation = rec))
                evals.remove(item.exerciseId)
            }
        }
        val suggested = suggestedLevel()
        var nd = d.copy(applied = true, levelChosen = level.name)
        edit { copy(day = nd) }
        store.put(uid, "days", nd.date, nd)
        // ER-12: overrides are logged.
        store.put(uid, "recommendations", nd.date, (s.recs.firstOrNull { it.date == nd.date } ?: RecommendationRec(date = nd.date))
            .copy(chosenLevel = level.name, override = level != suggested))
        if (level == Level.REST) {
            for (ex in nd.exercises.filter { it.exerciseId.isNotBlank() }) {
                adaptive.recordSession(uid, ex.programId, ex.exerciseId, today, null, true)
            }
            nd = nd.copy(logged = true)
            edit { copy(day = nd) }
            store.put(uid, "days", nd.date, nd)
            saveSessionDoc(nd, Level.REST, null, null, emptySet())
            edit { copy(message = "Rest day saved") }
        }
        onReady()
    }

    fun timerMinutes(): Int {
        val level = s.day.levelChosen?.let { Level.valueOf(it) } ?: suggestedLevel()
        val mins = s.day.exercises.filter { it.dimension == "MINUTES" }.mapNotNull { targetFor(it, level) }
        return mins.sum().toInt()
    }

    fun finishTimer(elapsedSeconds: Int) {
        val d = s.day.copy(timerSeconds = elapsedSeconds)
        persistDay(d)
        // Prefill actual amounts from the timer; the person always confirms them (EX-4).
        val level = d.levelChosen?.let { Level.valueOf(it) } ?: suggestedLevel()
        val actuals = d.exercises.filter { it.exerciseId.isNotBlank() }.associate { ex ->
            val target = targetFor(ex, level) ?: 0.0
            val v = if (ex.dimension == "MINUTES" && d.exercises.count { it.dimension == "MINUTES" } == 1 && elapsedSeconds > 0)
                minOf(target, maxOf(1.0, Math.round(elapsedSeconds / 60.0).toDouble())) else target
            ex.exerciseId to v
        }
        edit { copy(log = LogDraft(actuals = actuals)) }
    }

    fun prepareLog() {
        if (s.log.actuals.isNotEmpty()) return
        val d = s.day
        val level = d.levelChosen?.let { Level.valueOf(it) } ?: suggestedLevel()
        edit { copy(log = LogDraft(actuals = d.exercises.filter { it.exerciseId.isNotBlank() }.associate { it.exerciseId to (targetFor(it, level) ?: 0.0) })) }
    }

    fun updateLog(f: LogDraft.() -> LogDraft) = edit { copy(log = log.f()) }

    /** Saves what was really done: to the engine (per exercise) and to the session log. */
    fun submitLog(onDone: () -> Unit) = work {
        val d = s.day
        if (d.logged) { onDone(); return@work }
        val level = d.levelChosen?.let { Level.valueOf(it) } ?: suggestedLevel()
        val l = s.log
        val exercises = d.exercises.filter { it.exerciseId.isNotBlank() }
        var anyShort = false; var anyMore = false; var allMissed = true
        val updated = exercises.map { ex ->
            val assigned = ex.doseValue ?: 0.0
            val actual = if (l.notDone) 0.0 else (l.actuals[ex.exerciseId] ?: 0.0)
            val missed = actual <= 0.0
            adaptive.recordSession(uid, ex.programId, ex.exerciseId, today, if (missed) null else actual, missed)
            if (!missed) allMissed = false
            if (!missed && actual < assigned || missed) anyShort = true
            if (actual > assigned) anyMore = true
            ex.copy(actualValue = actual)
        }
        val nd = d.copy(
            exercises = d.exercises.map { e -> updated.firstOrNull { it.exerciseId == e.exerciseId } ?: e },
            logged = true, symptomResponse = l.symptom?.name, stopReason = l.stopReason?.name
        )
        edit { copy(day = nd) }
        store.put(uid, "days", nd.date, nd)
        saveSessionDoc(nd, level, l.symptom, l.stopReason, l.newSymptoms, anyShort, anyMore, allMissed)
        edit { copy(message = if (allMissed) "Saved. Nothing to make up." else "Session saved") }
        onDone()
    }

    private suspend fun saveSessionDoc(
        d: DayRec, level: Level, symptom: SymptomResponse?, reason: StopReason?, newSymptoms: Set<String>,
        anyShort: Boolean = false, anyMore: Boolean = false, allMissed: Boolean = level == Level.REST
    ) {
        val items = d.exercises.filter { it.exerciseId.isNotBlank() }.map { e ->
            val actual = e.actualValue ?: 0.0
            ExerciseItem(
                exerciseId = e.exerciseId, name = e.name,
                reps = if (e.dimension == "REPETITIONS") actual.toInt() else null,
                durationMinutes = if (e.dimension != "REPETITIONS") actual.toInt() else null
            )
        }
        if (items.isEmpty()) return
        val session = ExerciseSession(
            id = d.date, patientId = uid, date = d.date, scheduledTime = d.suggestedTime.ifBlank { "10:30" },
            items = items
        )
        session.status = when {
            allMissed -> com.example.vitala.data.SessionStatus.SKIPPED
            anyMore -> com.example.vitala.data.SessionStatus.OVERDONE
            anyShort -> com.example.vitala.data.SessionStatus.CUT_SHORT
            else -> com.example.vitala.data.SessionStatus.DONE
        }
        session.symptomResponse = symptom
        session.stopReason = if (anyShort || allMissed) reason else null
        session.painBefore = d.pain?.let { if (it == 0) PainFeeling.NONE else if (it < 5) PainFeeling.SOME else PainFeeling.A_LOT }
        session.symptomResponseText = newSymptoms.filter { it != "None" }.joinToString(", ")
        if (level == Level.REST) session.why = "Rest day chosen"
        edit { copy(sessions = sessions.filter { it.id != session.id } + session) }
        background { health.saveSession(uid, session) }
    }

    fun confirmReview() = persistDay(s.day.copy(reviewConfirmed = true))

    // ------------------------------------------------------------------ daily check-in

    fun updateDraft(f: CheckInDraft.() -> CheckInDraft) = edit { copy(draft = draft.f()) }

    fun startCheckIn() {
        val existing = s.checkIns.firstOrNull { it.date == today }
        edit {
            copy(
                draft = CheckInDraft(
                    pain = existing?.painLevel, fatigue = existing?.fatigue, interference = existing?.interference,
                    recovery = existing?.recovery?.status, recoveryTime = existing?.recovery?.time
                )
                // keep `log`: it may already hold the minutes from today's timer
            )
        }
        go(Screen.CheckIn(0))
    }

    /** Recovery is asked after a session or flare in the last 48 hours (open question 2 in the brief). */
    fun needsRecovery(): Boolean {
        val from = today.minusDays(2)
        val flare = s.sessions.any {
            it.localDate >= from && (it.symptomResponse != null && it.symptomResponse != SymptomResponse.WITHIN_RANGE ||
                    it.status == com.example.vitala.data.SessionStatus.CUT_SHORT)
        }
        val d = s.day
        return flare || d.exercises.any { it.action == "DECREASE" } || d.symptomResponse.let { it != null && it != SymptomResponse.WITHIN_RANGE.name }
    }

    fun needsLogStep(): Boolean = s.day.date == today.toString() && s.day.applied && !s.day.logged && s.day.levelChosen != Level.REST.name

    fun checkInSteps(): List<String> = buildList {
        add("pain"); add("fatigue"); add("interference")
        if (needsRecovery()) add("recovery")
        if (needsLogStep()) add("log")
        add("context")
    }

    fun saveCheckIn() = work {
        val d = s.draft
        val recovery = d.recovery?.let { Recovery(it, if (it == RecoveryStatus.RETURNED) d.recoveryTime else null) }
        val ci = DailyCheckIn(
            date = today, painLevel = requireNotNull(d.pain), fatigue = requireNotNull(d.fatigue),
            interference = requireNotNull(d.interference), recovery = recovery
        )
        edit { copy(checkIns = checkIns.filter { it.date != today } + ci) }
        background { health.saveCheckIn(uid, ci) }
        if (d.context.isNotEmpty() || d.note.isNotBlank()) {
            val e = ContextEventRec(date = today.toString(), types = d.context.map { it.name }, note = d.note.trim())
            edit { copy(contexts = contexts + e) }
            store.put(uid, "contexts", e.id, e)
        }
        edit { copy(draft = CheckInDraft(), log = LogDraft()) }
        replaceTop(Screen.CheckInDone)
    }

    fun saveContext(types: Set<ContextType>, note: String) {
        if (types.isEmpty() && note.isBlank()) { back(); return }
        val e = ContextEventRec(date = today.toString(), types = types.map { it.name }, note = note.trim())
        edit { copy(contexts = contexts + e, message = "Saved") }
        store.put(uid, "contexts", e.id, e)
        back()
    }

    /** Text for the "tomorrow" card: the engine's assigned dose and time; the pain rating confirms it. */
    fun tomorrowLines(): List<String> {
        val tomorrow = today.plusDays(1)
        return todayItems().map { item ->
            val p = item.compiled.plan
            val day = ChronoUnit.DAYS.between(p.programStart, tomorrow).toInt() + 1
            val ceiling = p.ceilings.firstOrNull { day in it.firstDay..it.lastDay }?.maximum
            val dose = item.state?.assignedDose?.value?.let { v -> ceiling?.let { minOf(v, it) } ?: v }
            "${item.name}|${timeFor(item)}|${dose?.let { Dose.fmt(item.dimension, it) } ?: ""}|" +
                    (item.state?.lastIncreaseDate?.plusDays(p.progression.minimumDaysBetweenIncreases.toLong())?.let { "Your plan allows an increase no sooner than ${fmtShort(it)}, if your pain stays steady." } ?: "")
        }
    }

    // ------------------------------------------------------------------ weekly PEG

    fun pegDue(): Boolean {
        if (s.plans.none { it.current }) return false
        s.settings.pegPostponedUntil?.let { if (LocalDate.parse(it) > today) return false }
        val last = s.pegs.maxOfOrNull { it.date }
        if (last == null) {
            val start = s.plans.minOfOrNull { it.savedOn }?.let(LocalDate::parse) ?: return false
            return today >= start.plusDays(6) || today.dayOfWeek.value == s.settings.weeklyDay && today > start
        }
        return ChronoUnit.DAYS.between(last, today) >= 7
    }

    fun postponePeg() = saveSettings { copy(pegPostponedUntil = today.plusDays(1).toString()) }

    fun setPeg(i: Int, v: Int) = edit { copy(weeklyDraft = weeklyDraft.toMutableList().also { it[i] = v }) }

    fun submitPeg() = work {
        val w = s.weeklyDraft
        val peg = PegResponse(today, w[0]!!, w[1]!!, w[2]!!)
        edit { copy(pegs = pegs.filter { it.date != today } + peg, weeklyDraft = listOf(null, null, null)) }
        background { health.savePeg(uid, peg) }
        saveSettings { copy(pegPostponedUntil = null) }
        edit { copy(message = "Weekly check-in saved") }
        toHome()
    }

    // ------------------------------------------------------------------ medicines

    fun saveMedication(m: MedicationRec) {
        val clean = if (m.asNeeded) m.copy(times = emptyList()) else m
        edit { copy(meds = meds.filter { it.id != m.id } + clean) }
        store.put(uid, "medications", m.id, clean)
        rescheduleReminders()
    }

    fun stopMedication(id: String) {
        s.meds.firstOrNull { it.id == id }?.let { saveMedication(it.copy(stopped = true, endDate = today.toString())) }
    }

    fun logMed(medId: String, time: String?, action: MedAction) {
        val ev = MedEvent(medicationId = medId, scheduledTime = time, action = action.name)
        edit { copy(medEvents = medEvents + ev) }
        store.put(uid, "medEvents", ev.id, ev)
    }

    fun medsToday(): List<MedicationRec> = s.meds.filter { !it.stopped && (it.endDate == null || LocalDate.parse(it.endDate) >= today) &&
            (it.startDate == null || LocalDate.parse(it.startDate) <= today) }

    fun medStatus(medId: String, time: String?): String {
        val e = s.medEvents.filter { it.medicationId == medId && it.date == today.toString() && it.scheduledTime == time }
            .filter { it.action != MedAction.SNOOZED.name }.lastOrNull()
        if (e != null) return if (e.action == MedAction.TAKEN.name) "Taken" else "Skipped"
        val t = time?.toTime() ?: return ""
        val now = LocalTime.now()
        return if (now.isBefore(t.minusMinutes(30))) "Later" else if (now.isAfter(t.plusHours(2))) "Missed" else "Due"
    }

    // ------------------------------------------------------------------ calendar and suggested times

    fun refreshSuggestion(): TimeSuggestion? {
        val items = sessionsToday().firstOrNull() ?: return null
        val (time, group) = items
        if (!s.settings.calendarLinked || !s.settings.suggestTimes) return null
        val busy = CalendarAvailability.busy(context, today)
        if (busy == null) { edit { copy(calendarNote = "We can't read your calendar right now, so we're using the times you set.", timeSuggestion = null) }; return null }
        val mins = group.sumOf { (it.upTo ?: 0.0).toInt().takeIf { _ -> it.dimension == DoseDimension.MINUTES } ?: 5 }
        val meds = medsToday().filter { !it.asNeeded }.flatMap { it.times }.map { it.toTime() }
        val sug = CalendarAvailability.suggest(busy, time.toTime(), mins, quiet(), meds)
        edit { copy(timeSuggestion = sug, calendarNote = null) }
        return sug
    }

    fun linkCalendar(source: String) = saveSettings { copy(calendarLinked = true, calendarSource = source) }
    fun unlinkCalendar() {
        saveSettings { copy(calendarLinked = false, calendarSource = "") }
        edit { copy(timeSuggestion = null, calendarNote = null) }
    }

    /** CAL-4: changes that day only, unless "every day" is chosen. */
    fun acceptTime(time: LocalTime, everyDay: Boolean) {
        val group = sessionsToday().firstOrNull()?.second ?: return
        saveSettings {
            if (everyDay) copy(exerciseTimes = exerciseTimes.map { e -> if (group.any { it.exerciseId == e.exerciseId }) e.copy(time = time.hhmm()) else e })
            else copy(dayTimes = dayTimes + (LocalDate.now().toString() to group.associate { it.exerciseId to time.hhmm() }))
        }
        edit { copy(day = day.copy(suggestedTime = time.hhmm())) }
    }

    fun quiet() = QuietHours(s.settings.quietOn, s.settings.quietStart, s.settings.quietEnd)

    // ------------------------------------------------------------------ reminders

    fun rescheduleReminders() {
        if (s.uid == null) return
        val st = s.settings
        val specs = mutableListOf<ReminderSpec>()
        if (st.exerciseRemindersOn) {
            val items = todayItems()
            st.exerciseTimes.filter { it.enabled }.forEach { et ->
                val time = et.time.toTime()
                val at = time.minusMinutes(et.leadMinutes.toLong())
                if (items.isNotEmpty() && items.none { it.exerciseId == et.exerciseId }) return@forEach
                specs += ReminderSpec(
                    key = "ex-${et.exerciseId.ifBlank { et.exerciseName }}", kind = ReminderKind.EXERCISE.name,
                    title = "Time for ${et.exerciseName}", body = "Your session is at ${fmtTime(time)}.",
                    time = at.hhmm(), exerciseTime = et.time
                )
            }
        }
        if (st.medRemindersOn) {
            s.meds.filter { !it.stopped && it.remindersOn && !it.asNeeded }.forEach { m ->
                m.times.forEach { t ->
                    specs += ReminderSpec(
                        key = "med-${m.id}-$t", kind = ReminderKind.MEDICINE.name,
                        title = if (st.showMedNames) "Time for ${m.name}" else "Time for your medicine",
                        body = if (st.showMedNames) listOf(m.dose, m.instructions).filter { it.isNotBlank() }.joinToString(" · ") else "Open the app for details.",
                        time = t, important = m.important, medId = m.id, medTime = t,
                        privateTitle = "Time for your medicine"
                    )
                }
            }
        }
        if (st.checkInRemindersOn) specs += ReminderSpec("checkin", ReminderKind.CHECKIN.name, "Daily check-in", "About 3 minutes.", st.checkInTime)
        if (st.weeklyOn) specs += ReminderSpec("weekly", ReminderKind.WEEKLY.name, "Weekly check-in", "3 quick questions, about 1 minute.", st.weeklyTime, weekday = st.weeklyDay)
        Reminders.createChannels(context)
        Reminders.scheduleAll(context, specs, quiet())
    }

    // ------------------------------------------------------------------ insights and report

    private fun patientForReport(): Patient {
        val exercises = todayItems().mapNotNull { item ->
            val v = item.compiled.plan.startingDose.value.toInt()
            when (item.dimension) {
                DoseDimension.REPETITIONS -> PrescribedExercise(name = item.name, sets = 1, reps = v)
                else -> PrescribedExercise(name = item.name, durationMinutes = maxOf(1, v))
            }
        }
        return Patient(
            id = uid, name = s.profile.fullName.ifBlank { "Patient" },
            heightCm = s.profile.heightCm ?: 170.0, weightKg = s.profile.weightKg ?: 70.0,
            affectedJoints = s.activeConditions.map { it.name }.ifEmpty { listOf("Not recorded") },
            exercise = exercises
        )
    }

    /** Builds the doctor report on the device and saves it (EP-4); sharing is done by the user. */
    fun exportReport(onSaved: (ReportSaver.Saved, String) -> Unit) = work {
        val from = today.minusDays(ReportData.DAYS - 1)
        val data: ReportData = ReportData.build(
            today = today, now = Instant.now(), patient = patientForReport(),
            checkIns = health.checkIns(uid, from, today), sessions = health.sessions(uid, from, today), peg = health.peg(uid, from, today)
        )
        val name = ReportPdf.fileName(today)
        val saved = withContext(Dispatchers.IO) { ReportSaver.save(context, name) { out -> ReportPdf.write(data, out) } }
        onSaved(saved, name)
    }

    fun editCheckIn(date: LocalDate, pain: Int, fatigue: Int, interference: Int, recovery: RecoveryStatus?, time: RecoveryTime?) {
        val old = s.checkIns.firstOrNull { it.date == date }
        val ci = DailyCheckIn(
            date = date, painLevel = pain, fatigue = fatigue, interference = interference,
            recovery = recovery?.let { Recovery(it, if (it == RecoveryStatus.RETURNED) time else null) }
        )
        edit { copy(checkIns = checkIns.filter { it.date != date } + ci, message = "Entry updated") }
        background { health.saveCheckIn(uid, ci) }
        val log = EditLogRec(date = date.toString(),
            before = old?.let { "pain ${it.painLevel}, fatigue ${it.fatigue}, daily life ${it.interference}" } ?: "none",
            after = "pain $pain, fatigue $fatigue, daily life $interference")
        store.put(uid, "edits", log.id, log)
        back()
    }

    // ------------------------------------------------------------------ account

    fun exportMyData(onReady: (Uri) -> Unit) = work {
        val raw = store.rawAll(uid, listOf("conditions", "plans", "medications", "medEvents", "contexts", "recommendations", "days", "edits"))
        val all = mapOf(
            "profile" to s.profile, "settings" to s.settings, "stored" to raw,
            "dailyCheckIns" to s.checkIns.map { mapOf("date" to it.date.toString(), "pain" to it.painLevel, "fatigue" to it.fatigue, "interference" to it.interference, "recovery" to it.recovery?.label) },
            "sessions" to s.sessions.map { mapOf("date" to it.date, "status" to it.status.name, "symptomResponse" to it.symptomResponse?.name, "items" to it.items.map { i -> i.name }) },
            "peg" to s.pegs.map { mapOf("date" to it.date.toString(), "pain" to it.pain, "enjoyment" to it.enjoyment, "activity" to it.activity) }
        )
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            File(dir, "vitala-my-data.json").also { it.writeText(store.gson.newBuilder().setPrettyPrinting().create().toJson(all)) }
        }
        onReady(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }

    fun deleteAccount() {
        if (local != null) { leaveDemo(); return }
        deleteCloudAccount()
    }

    private fun deleteCloudAccount() = work {
        val id = uid
        store.deleteEverything(id)
        File(context.filesDir, "plans").deleteRecursively()
        File(context.filesDir, "photos").deleteRecursively()
        Reminders.clear(context)
        val user = auth.currentUser
        try {
            user?.delete()?.await()
        } catch (e: Exception) {
            edit { copy(error = "Your data was deleted, but we need you to sign in again to remove the sign-in itself. Sign in, then delete again.") }
            auth.signOut()
            return@work
        }
        auth.signOut()
    }

    // ------------------------------------------------------------------ shared formatting

    companion object {
        fun fmtTime(t: LocalTime): String {
            val h = if (t.hour % 12 == 0) 12 else t.hour % 12
            return "$h:%02d %s".format(t.minute, if (t.hour < 12) "am" else "pm")
        }
        fun fmtShort(d: LocalDate) = d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + " " + d.dayOfMonth
    }
}
