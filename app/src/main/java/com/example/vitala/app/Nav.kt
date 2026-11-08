package com.example.vitala.app

import java.time.LocalDate

enum class Tab { HOME, INSIGHTS, PLAN, SETTINGS }

sealed interface Screen {
    data class TabRoot(val tab: Tab) : Screen
    // sign in + onboarding
    data object Auth : Screen
    data object AboutYou : Screen
    data object SetupDay : Screen
    data class AddCondition(val onboarding: Boolean = false) : Screen
    data class AddPlan(val conditionId: String, val onboarding: Boolean = false) : Screen
    data class ManualPlan(val conditionId: String, val onboarding: Boolean = false) : Screen
    data class PlanReviewScreen(val conditionId: String, val source: String, val onboarding: Boolean = false) : Screen
    data class ReminderTimes(val onboarding: Boolean = false, val fromSave: Boolean = true) : Screen
    data object OnboardMedicines : Screen
    data object OnboardCalendar : Screen

    // exercise flow
    data class ExerciseDetail(val time: String) : Screen
    data object BeforeStart : Screen
    data object SafetyQuestions : Screen
    data object SessionToday : Screen
    data object ChooseLevel : Screen
    data object Timer : Screen
    data class ExerciseLog(val fromCheckIn: Boolean = false) : Screen
    data object SafetyBlock : Screen
    data object SuggestedTime : Screen

    // check-ins
    data class CheckIn(val step: Int) : Screen
    data object CheckInDone : Screen
    data class Weekly(val step: Int) : Screen
    data object SomethingChanged : Screen

    // insights
    data object InsightsMonth : Screen
    data class DayDetail(val date: LocalDate) : Screen
    data class EditDay(val date: LocalDate) : Screen
    data object ExportPdf : Screen

    // plan
    data class UpdatePlan(val conditionId: String) : Screen
    data class PlanHistory(val conditionId: String) : Screen
    data class PlanVersionView(val programId: String) : Screen
    data class PlanPhotos(val programId: String) : Screen
    data class PlanItemDetail(val programId: String, val key: String) : Screen

    // settings
    data object Conditions : Screen
    data class EditCondition(val id: String) : Screen
    data object Medicines : Screen
    data class EditMedicine(val id: String?) : Screen
    data object Health : Screen
    data object Notifications : Screen
    data object Calendar : Screen
    data object TextSize : Screen
    data object Account : Screen
    data object EditAccount : Screen
    data object DeleteAccount : Screen

    // reminders opened from notifications
    data class MedicineReminder(val medId: String, val time: String) : Screen
    data class ExerciseReminder(val time: String) : Screen
}
