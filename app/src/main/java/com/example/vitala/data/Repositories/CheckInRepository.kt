package com.example.vitala.data.Repositories

import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.WeeklyCheckIn
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.time.LocalDate

/**
 * A repository for managing daily and weekly check-ins.
 */
class CheckInRepository {
    private val db = Firebase.firestore
    private val dailyCheckInsCollection = db.collection("dailyCheckIns")
    private val weeklyCheckInsCollection = db.collection("weeklyCheckIns")

    private val _dailyCheckIns = mutableListOf<DailyCheckIn>()
    val dailyCheckIns: List<DailyCheckIn> = _dailyCheckIns

    private val _weeklyCheckIns = mutableListOf<WeeklyCheckIn>()
    val weeklyCheckIns: List<WeeklyCheckIn> = _weeklyCheckIns

    init {
        dailyCheckInsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            _dailyCheckIns.clear()
            snapshot?.forEach { doc ->
                val checkIn = doc.toObject(DailyCheckIn::class.java)
                _dailyCheckIns.add(checkIn)
            }
        }

        weeklyCheckInsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            _weeklyCheckIns.clear()
            snapshot?.forEach { doc ->
                val checkIn = doc.toObject(WeeklyCheckIn::class.java)
                _weeklyCheckIns.add(checkIn)
            }
        }
    }

    fun saveDailyCheckIn(checkIn: DailyCheckIn) {
        dailyCheckInsCollection.document(checkIn.id).set(checkIn)
    }

    fun getDailyCheckIn(date: LocalDate): DailyCheckIn? {
        return _dailyCheckIns.find { it.date == date }
    }

    fun saveWeeklyCheckIn(checkIn: WeeklyCheckIn) {
        weeklyCheckInsCollection.document(checkIn.id).set(checkIn)
    }
}
