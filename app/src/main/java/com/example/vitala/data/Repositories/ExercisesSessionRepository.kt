package com.example.vitala.data.Repositories

import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.Patient
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class ExercisesSessionRepository {
    val db = Firebase.firestore
    val exercisesSessionCollection = db.collection("exercisesSession")
    val _sessions = mutableListOf<ExerciseSession>()




    init {
        exercisesSessionCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Handle error
                return@addSnapshotListener
            }
            _sessions.clear()
            snapshot?.forEach { document ->
                val session = document.toObject(ExerciseSession::class.java)
                _sessions.add(session)
            }
        }
    }

    fun addSession(session: ExerciseSession) {
        exercisesSessionCollection.add(session)
    }

    fun getSessions(patientId: String): List<ExerciseSession> {
        return _sessions.filter { it.patientId == patientId }
    }


}