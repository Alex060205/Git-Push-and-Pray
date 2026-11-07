package com.example.vitala
/**
 * One place to get the repositories from anywhere in the app.
 * Later, swap the InMemory classes for Room/Firestore ones here; nothing else changes.
 *
 * Call AppContainer.init() once in MainActivity.onCreate().
 */
object AppContainer {
    val patientRepository: PatientRepository = InMemoryPatientRepository()
    val checkInRepository: CheckInRepository = InMemoryCheckInRepository()
    val activityLogRepository: ActivityLogRepository = InMemoryActivityLogRepository()

    fun init() {
        DemoDataSeeder(patientRepository, checkInRepository, activityLogRepository).seedIfEmpty()
    }
}