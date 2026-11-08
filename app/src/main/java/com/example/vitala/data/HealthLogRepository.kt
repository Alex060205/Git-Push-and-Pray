package com.example.vitala.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime

/**
 * Stores daily check-ins, exercise sessions and weekly PEG answers in Firestore:
 *
 *   patients/{patientId}/checkIns/{yyyy-MM-dd}   one per day; saving the same day again replaces it
 *   patients/{patientId}/sessions/{sessionId}
 *   patients/{patientId}/peg/{yyyy-MM-dd}
 *
 * Dates are stored as "yyyy-MM-dd" strings and enums by name. Firestore can't store LocalDate
 * directly, and ISO date strings sort correctly, so date-range queries need no extra index.
 */
class HealthLogRepository(private val db: FirebaseFirestore = Firebase.firestore) {

    private fun patient(id: String) = db.collection("patients").document(id)
    private fun checkInsOf(id: String) = patient(id).collection("checkIns")
    private fun sessionsOf(id: String) = patient(id).collection("sessions")
    private fun pegOf(id: String) = patient(id).collection("peg")

    // ---------- Daily check-ins ----------

    suspend fun saveCheckIn(patientId: String, checkIn: DailyCheckIn) {
        checkInsOf(patientId).document(checkIn.date.toString()).set(checkIn.toFirestore()).await()
    }

    /** Check-ins from [from] to [to] inclusive, oldest first. */
    suspend fun checkIns(patientId: String, from: LocalDate, to: LocalDate): List<DailyCheckIn> =
        checkInsOf(patientId)
            .whereGreaterThanOrEqualTo("date", from.toString())
            .whereLessThanOrEqualTo("date", to.toString())
            .orderBy("date")
            .get().await()
            .documents.mapNotNull { it.toCheckIn() }

    // ---------- Exercise sessions ----------

    suspend fun saveSession(patientId: String, session: ExerciseSession) {
        sessionsOf(patientId).document(session.id).set(session.toFirestore()).await()
    }

    /** Sessions from [from] to [to] inclusive, oldest first. */
    suspend fun sessions(patientId: String, from: LocalDate, to: LocalDate): List<ExerciseSession> =
        sessionsOf(patientId)
            .whereGreaterThanOrEqualTo("date", from.toString())
            .whereLessThanOrEqualTo("date", to.toString())
            .orderBy("date")
            .get().await()
            .documents.mapNotNull { it.toSession() }

    // ---------- Weekly PEG ----------

    suspend fun savePeg(patientId: String, peg: PegResponse) {
        pegOf(patientId).document(peg.date.toString()).set(peg.toFirestore()).await()
    }

    suspend fun peg(patientId: String, from: LocalDate, to: LocalDate): List<PegResponse> =
        pegOf(patientId)
            .whereGreaterThanOrEqualTo("date", from.toString())
            .whereLessThanOrEqualTo("date", to.toString())
            .orderBy("date")
            .get().await()
            .documents.mapNotNull { it.toPeg() }

    // ---------- Mapping ----------

    private fun DailyCheckIn.toFirestore(): Map<String, Any?> = mapOf(
        "date" to date.toString(),
        "painLevel" to painLevel,
        "fatigue" to fatigue,
        "interference" to interference,
        "recoveryStatus" to recovery?.status?.name,
        "recoveryTime" to recovery?.time?.name,
        "painDuration" to painDuration?.name,
        "sleepHours" to sleepHours,
        "mood" to mood?.name,
        "nutrition" to nutrition?.name,
        "tookAllMedications" to tookAllMedications,
        "exerciseIntensity" to exerciseIntensity?.name,
        // weather is not stored yet: WeatherSnapshot needs its own mapping.
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.toCheckIn(): DailyCheckIn? = runCatching {
        DailyCheckIn(
            id = id,
            date = LocalDate.parse(getString("date")!!),
            painLevel = getLong("painLevel")!!.toInt(),
            fatigue = getLong("fatigue")!!.toInt(),
            interference = getLong("interference")!!.toInt(),
            recovery = enumOf<RecoveryStatus>("recoveryStatus")?.let { Recovery(it, enumOf<RecoveryTime>("recoveryTime")) },
            painDuration = enumOf<PainDuration>("painDuration"),
            sleepHours = getDouble("sleepHours"),
            mood = enumOf<Mood>("mood"),
            nutrition = enumOf<Nutrition>("nutrition"),
            tookAllMedications = getBoolean("tookAllMedications"),
            exerciseIntensity = enumOf<Intensity>("exerciseIntensity")
        )
    }.onFailure { Log.w(TAG, "Skipping unreadable check-in $id", it) }.getOrNull()

    private fun ExerciseSession.toFirestore(): Map<String, Any?> = mapOf(
        "patientId" to patientId,
        "date" to date,
        "scheduledTime" to scheduledTime,
        "status" to status.name,
        "painBefore" to painBefore?.name,
        "painDuring" to painDuring?.name,
        "painAfter" to painAfter?.name,
        "symptomResponse" to symptomResponse?.name,
        "stopReason" to stopReason?.name,
        "why" to why,
        "symptomResponseText" to symptomResponseText,
        // TODO: store full ExerciseItems once their fields are known. Names only for now.
        "itemNames" to items.map { it.name },
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.toSession(): ExerciseSession? = runCatching {
        ExerciseSession(
            id = id,
            patientId = getString("patientId").orEmpty(),
            date = getString("date")!!.also { LocalDate.parse(it) },          // reject malformed dates
            scheduledTime = (getString("scheduledTime") ?: "00:00").also { LocalTime.parse(it) },
            items = emptyList(), // TODO: read ExerciseItems back (see toFirestore)
            status = enumOf<SessionStatus>("status") ?: SessionStatus.PLANNED,
            painBefore = enumOf<PainFeeling>("painBefore"),
            painDuring = enumOf<PainFeeling>("painDuring"),
            painAfter = enumOf<PainFeeling>("painAfter"),
            symptomResponse = enumOf<SymptomResponse>("symptomResponse"),
            stopReason = enumOf<StopReason>("stopReason"),
            why = getString("why").orEmpty(),
            symptomResponseText = getString("symptomResponseText")
                ?: getString("symptomResponse").orEmpty()
        )
    }.onFailure { Log.w(TAG, "Skipping unreadable session $id", it) }.getOrNull()

    private fun PegResponse.toFirestore(): Map<String, Any?> = mapOf(
        "date" to date.toString(),
        "pain" to pain,
        "enjoyment" to enjoyment,
        "activity" to activity,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.toPeg(): PegResponse? = runCatching {
        PegResponse(
            date = LocalDate.parse(getString("date")!!),
            pain = getLong("pain")!!.toInt(),
            enjoyment = getLong("enjoyment")!!.toInt(),
            activity = getLong("activity")!!.toInt()
        )
    }.onFailure { Log.w(TAG, "Skipping unreadable PEG $id", it) }.getOrNull()

    private inline fun <reified E : Enum<E>> DocumentSnapshot.enumOf(field: String): E? =
        getString(field)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    private companion object {
        const val TAG = "HealthLogRepository"
    }
}