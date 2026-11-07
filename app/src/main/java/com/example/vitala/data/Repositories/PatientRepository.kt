package com.example.vitala.data.Repositories

import com.example.vitala.data.Patient
import com.example.vitala.data.TreatmentPlan
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * A repository for patients.
 */
class PatientRepository {
    private val db = Firebase.firestore
    private val patientsCollection = db.collection("patients")

    private val _patients = mutableListOf<Patient>()
    val patients: List<Patient> = _patients

    init {
        patientsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Handle error
                return@addSnapshotListener
            }
            _patients.clear()
            snapshot?.forEach { document ->
                val patient = document.toObject(Patient::class.java)
                _patients.add(patient)
            }
        }
    }

    /**
     * Gets a patient by their ID.
     * @param id The ID of the patient
     * @return The patient with the given ID, or null if not found
     */
    fun getPatient(id: String): Patient? {
        return patients.find { it.id == id }
    }

    /**
     * Saves/add/update a patient to the database.
     * @param patient The patient to save
     */
    fun savePatient(patient: Patient) {
        patientsCollection.document(patient.id).set(patient)
    }

    /**
     * Gets the active plan for the patient.
     * @return The active plan, or null if there is no active plan
     */
    fun getActivePlan(patientId: String): TreatmentPlan? {
        return patients.find { it.id == patientId }?.treatmentPlan
    }
}