package com.example.vitala.app

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await

/**
 * Stores the app-level records under patients/{uid}/... as {"json": "..."} documents.
 *
 * Writes are NOT awaited: Firestore queues them on the device and syncs later, so the app keeps
 * working with no connection (requirement: offline-first). Reads fall back to the local cache.
 *
 *   patients/{uid}                       profile + settings (fields profileJson / settingsJson)
 *   patients/{uid}/conditions/{id}
 *   patients/{uid}/plans/{programId}
 *   patients/{uid}/medications/{id}
 *   patients/{uid}/medEvents/{id}
 *   patients/{uid}/contexts/{id}
 *   patients/{uid}/recommendations/{date}
 *   patients/{uid}/days/{date}
 *   patients/{uid}/edits/{id}
 */
class UserStore(private val db: FirebaseFirestore = Firebase.firestore) : DataStore {
    override val gson = Gson()

    private fun root(uid: String) = db.collection("patients").document(uid)

    override suspend fun loadRoot(uid: String): Pair<Profile?, Settings?> {
        val d = root(uid).get().await()
        val p = d.getString("profileJson")?.let { runCatching { gson.fromJson(it, Profile::class.java) }.getOrNull() }
        val s = d.getString("settingsJson")?.let { runCatching { gson.fromJson(it, Settings::class.java) }.getOrNull() }
        return p to s
    }

    override fun saveProfile(uid: String, p: Profile) {
        root(uid).set(mapOf("profileJson" to gson.toJson(p)), com.google.firebase.firestore.SetOptions.merge())
    }

    override fun saveSettings(uid: String, s: Settings) {
        root(uid).set(mapOf("settingsJson" to gson.toJson(s)), com.google.firebase.firestore.SetOptions.merge())
    }

    override fun <T> put(uid: String, collection: String, id: String, value: T) {
        root(uid).collection(collection).document(id).set(mapOf("json" to gson.toJson(value)))
    }

    override fun remove(uid: String, collection: String, id: String) {
        root(uid).collection(collection).document(id).delete()
    }

    override suspend fun <T> list(uid: String, collection: String, cls: Class<T>): List<T> =
        root(uid).collection(collection).get().await().documents.mapNotNull { d ->
            d.getString("json")?.let { runCatching { gson.fromJson(it, cls) }.getOrNull() }
        }

    override suspend fun <T> get(uid: String, collection: String, id: String, cls: Class<T>): T? =
        root(uid).collection(collection).document(id).get().await().getString("json")
            ?.let { runCatching { gson.fromJson(it, cls) }.getOrNull() }

    /** Raw JSON for export. */
    override suspend fun rawAll(uid: String, collections: List<String>): Map<String, List<String>> =
        collections.associateWith { c ->
            root(uid).collection(c).get().await().documents.mapNotNull { it.getString("json") ?: it.data?.toString() }
        }

    /**
     * Deletes everything stored for the user (account deletion). Firestore cannot delete a
     * collection from the client in one call, so each known collection is walked.
     */
    override suspend fun deleteEverything(uid: String) {
        val simple = listOf(
            "conditions", "plans", "medications", "medEvents", "contexts", "recommendations", "days",
            "edits", "checkIns", "sessions", "peg", "adaptivePrograms"
        )
        for (c in simple) {
            val docs = root(uid).collection(c).get().await().documents
            for (d in docs) {
                if (c == "adaptivePrograms") {
                    for (e in d.reference.collection("exercises").get().await().documents) {
                        for (day in e.reference.collection("days").get().await().documents) day.reference.delete().await()
                        e.reference.delete().await()
                    }
                }
                d.reference.delete().await()
            }
        }
        root(uid).delete().await()
    }
}
