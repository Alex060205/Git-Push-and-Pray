package com.example.vitala.data.Repositories

import com.example.vitala.Recognition.ExercisePrescription
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * A repository for managing exercise prescriptions extracted from scans.
 */
class PrescriptionRepository {
    private val db = Firebase.firestore
    private val prescriptionsCollection = db.collection("prescriptions")

    private val _prescriptions = mutableListOf<ExercisePrescription>()
    val prescriptions: List<ExercisePrescription> = _prescriptions

    init {
        prescriptionsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            _prescriptions.clear()
            snapshot?.forEach { document ->
                val prescription = document.toObject(ExercisePrescription::class.java)
                _prescriptions.add(prescription)
            }
        }
    }

    /**
     * Saves a prescription to Firestore.
     */
    fun savePrescription(prescription: ExercisePrescription) {
        prescriptionsCollection.add(prescription)
    }

    /**
     * Gets all saved prescriptions.
     */
    fun getAllPrescriptions(): List<ExercisePrescription> = _prescriptions
}
