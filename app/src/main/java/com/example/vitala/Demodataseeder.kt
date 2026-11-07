package com.example.vitala

import com.example.vitala.data.ActivityLogRepository
import com.example.vitala.data.AvailabilityPreferences
import com.example.vitala.data.BodyMetrics
import com.example.vitala.data.CheckInRepository
import com.example.vitala.data.DailyCheckIn
import com.example.vitala.data.DoseStatus
import com.example.vitala.data.ExerciseItem
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.Intensity
import com.example.vitala.data.Medication
import com.example.vitala.data.MedicationLog
import com.example.vitala.data.MedicationType
import com.example.vitala.data.MetricsSource
import com.example.vitala.data.Mood
import com.example.vitala.data.Nutrition
import com.example.vitala.data.PainDuration
import com.example.vitala.data.PainFeeling
import com.example.vitala.data.Patient
import com.example.vitala.data.PatientRepository
import com.example.vitala.data.PrescribedExercise
import com.example.vitala.data.TimeBlock
import com.example.vitala.data.TreatmentPlan
import com.example.vitala.data.WeatherSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.round
import kotlin.random.Random

/**
 * Fills the repositories with a demo patient, the treatment plan from the mockups,
 * and [days] days of history ending yesterday.
 *
 * The fake history has patterns built in so TrendAnalyzer has something to find:
 *  - pain goes down over time (the plan is working)
 *  - 7+ hours of sleep -> less pain
 *  - exercise done -> less pain
 *  - rainy days -> more pain
 *
 * Same seed = same data every run, so the demo is repeatable.
 */
class DemoDataSeeder(
    private val patients: PatientRepository,
    private val checkIns: CheckInRepository,
    private val logs: ActivityLogRepository,
    private val random: Random = Random(42)
) {

    /**
     * Fills the repositories with a demo patient, the treatment plan from the mockups,
     * and [days] days of history ending yesterday.
     * If the repositories already have data, does nothing.
     */
    fun seedIfEmpty(today: LocalDate = LocalDate.now(), days: Int = 14) {
        if (patients.getPatient() != null) return
        seed(today, days)
    }

    fun seed(today: LocalDate, days: Int) {
        val start = today.minusDays(days.toLong())
        patients.savePatient(demoPatient(start))
        patients.addPlan(demoPlan(start))
        val plan = patients.getActivePlan() ?: return

        var previousPain = 6
        for (daysAgo in days downTo 1) {
            val date = today.minusDays(daysAgo.toLong())
            val progress = (days - daysAgo).toDouble() / (days - 1).coerceAtLeast(1) // 0 -> 1

            val weather = randomWeather(date)
            val isRainy = weather.condition == "Rain"
            val sleepHours = (5.0 + random.nextDouble() * 3.5).roundTo1()       // 5.0 - 8.5

            // Exercise (none on Sundays: "All day Sundays" is a time to avoid)
            val hasSession = date.dayOfWeek != DayOfWeek.SUNDAY
            val intensity = when {
                previousPain >= 9 -> Intensity.REST
                previousPain >= 7 -> Intensity.GENTLE
                else -> Intensity.FULL
            }
            val didExercise = hasSession && random.nextDouble() > 0.15

            // Pain for the day
            var pain = 7.0 - 3.5 * progress
            if (sleepHours >= 7.0) pain -= 2.5
            if (didExercise) pain -= 0.8
            if (isRainy) pain += 1.0
            pain += random.nextDouble(-0.7, 0.7)
            val painLevel = round(pain).toInt().coerceIn(0, 10)

            if (hasSession) {
                saveSession(plan, date, intensity, didExercise, painLevel)
            }
            val allTaken = saveDoses(plan, date, painLevel)

            checkIns.save(
                DailyCheckIn(
                    date = date,
                    painLevel = painLevel,
                    painDuration = durationFor(painLevel),
                    sleepHours = sleepHours,
                    mood = when {
                        painLevel <= 3 -> Mood.GOOD
                        painLevel <= 6 -> Mood.OKAY
                        else -> Mood.BAD
                    },
                    nutrition = listOf(
                        Nutrition.GOOD,
                        Nutrition.GOOD,
                        Nutrition.OKAY,
                        Nutrition.POOR
                    )
                        .random(random),
                    tookAllMedications = allTaken,
                    weather = weather
                )
            )
            previousPain = painLevel
        }
    }

    // ---------- Demo patient and plan (matches the mockups) ----------

    private fun demoPatient(since: LocalDate) = Patient(
        name = "Maria",
        birthDate = LocalDate.of(1951, 3, 14),
        bodyMetrics = BodyMetrics(
            heightCm = 165.0,
            weightKg = 68.0,
            affectedJoints = listOf("Left knee", "Right knee"),
            updatedOn = since,
            source = MetricsSource.DOCTOR_VISIT
        ),
        availability = AvailabilityPreferences(
            listOf(
                TimeBlock(start = LocalTime.MIN, end = LocalTime.of(9, 0)),
                TimeBlock(start = LocalTime.NOON, end = LocalTime.of(13, 0)),
                TimeBlock(start = LocalTime.of(19, 0), end = LocalTime.MAX),
                TimeBlock(
                    start = LocalTime.MIN,
                    end = LocalTime.MAX,
                    days = setOf(DayOfWeek.SUNDAY)
                )
            )
        ),
    )

    private fun demoPlan(createdOn: LocalDate) = TreatmentPlan(
        physicianName = "Dr. Garcia",
        createdOn = createdOn,
        exercises = listOf(
            PrescribedExercise(
                name = "Knee bends",
                sets = 2,
                reps = 10,
                instructions = "Hold a chair for balance."
            ),
            PrescribedExercise(
                name = "Leg raises", sets = 2, reps = 8,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            ),
            PrescribedExercise(name = "Walk", durationMinutes = 15)
        ),
        medications = listOf(
            Medication(
                name = "Medicine A", dose = "1 tablet", type = MedicationType.SCHEDULED,
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), notes = "With food"
            ),
            Medication(
                name = "Medicine B", dose = "1 tablet", type = MedicationType.AS_NEEDED,
                notes = "Only when pain is high"
            )
        )
    )

    // ---------- History helpers ----------

    private fun saveSession(
        plan: TreatmentPlan,
        date: LocalDate,
        intensity: Intensity,
        done: Boolean,
        painLevel: Int
    ) {
        val session = ExerciseSession(
            date = date,
            scheduledTime = LocalTime.of(10, 30),
            intensity = intensity,
            items = itemsFor(plan, date, intensity)
        )
        if (!done) {
            logs.saveSession(session.markSkipped())
            return
        }
        val during = when {
            painLevel >= 7 -> PainFeeling.A_LOT
            painLevel >= 4 -> PainFeeling.SOME
            else -> PainFeeling.NONE
        }
        // Full intensity on a higher-pain day makes pain after a bit worse
        val after = if (intensity == Intensity.FULL && painLevel >= 5 && during != PainFeeling.A_LOT) {
            PainFeeling.entries[during.ordinal + 1]
        } else during
        logs.saveSession(session.markDone(during, after))
    }

    /** Saves the day's doses. Returns true if every scheduled dose was taken. */
    private fun saveDoses(plan: TreatmentPlan, date: LocalDate, painLevel: Int): Boolean {
        var allTaken = true
        for (med in plan.scheduledMedications()) {
            for (time in med.times) {
                val taken = random.nextDouble() < 0.9
                if (!taken) allTaken = false
                val log = MedicationLog(medicationId = med.id, date = date, scheduledTime = time)
                logs.saveDose(
                    if (taken) log.markTaken(date.atTime(time).plusMinutes(random.nextLong(0, 20)))
                    else log.markSkipped()
                )
            }
        }
        if (painLevel >= 7) {
            for (med in plan.asNeededMedications()) {
                logs.saveDose(
                    MedicationLog(
                        medicationId = med.id,
                        date = date,
                        takenAt = date.atTime(14, 0),
                        status = DoseStatus.TAKEN,
                        painLevelAtDose = painLevel
                    )
                )
            }
        }
        return allTaken
    }

    private fun itemsFor(plan: TreatmentPlan, date: LocalDate, intensity: Intensity): List<ExerciseItem> =
        when (intensity) {
            Intensity.REST -> listOf(
                ExerciseItem(
                    exerciseId = "rest-stretch",
                    name = "Light stretching",
                    durationMinutes = 5
                )
            )
            Intensity.GENTLE -> plan.exercisesFor(date).map { it.toItem(gentle = true) }
            Intensity.FULL -> plan.exercisesFor(date).map { it.toItem(gentle = false) }
        }

    private fun PrescribedExercise.toItem(gentle: Boolean) = ExerciseItem(
        exerciseId = id,
        name = name,
        sets = sets?.let { if (gentle) maxOf(1, it / 2) else it },
        reps = reps,
        durationMinutes = durationMinutes?.let { if (gentle) maxOf(5, it / 2) else it }
    )

    private fun randomWeather(date: LocalDate): WeatherSnapshot {
        val rainy = random.nextDouble() < 0.3
        return WeatherSnapshot(
            takenAt = date.atTime(8, 0),
            temperatureC = (2 + random.nextDouble() * 10).roundTo1(),
            condition = when {
                rainy -> "Rain"
                random.nextBoolean() -> "Cloudy"
                else -> "Sunny"
            },
            humidityPercent = if (rainy) 80 + random.nextInt(15) else 40 + random.nextInt(30),
            pressureHpa = if (rainy) 995.0 + random.nextInt(10) else 1010.0 + random.nextInt(15)
        )
    }

    private fun durationFor(painLevel: Int) = when {
        painLevel == 0 -> PainDuration.NONE
        painLevel <= 2 -> PainDuration.A_FEW_MOMENTS
        painLevel <= 4 -> PainDuration.A_FEW_HOURS
        painLevel <= 7 -> PainDuration.MOST_OF_THE_DAY
        else -> PainDuration.ALL_DAY
    }

    private fun Double.roundTo1() = round(this * 10) / 10
}