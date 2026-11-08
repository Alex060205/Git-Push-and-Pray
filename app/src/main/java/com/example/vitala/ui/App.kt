package com.example.vitala.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vitala.app.AppState
import com.example.vitala.app.AppViewModel
import com.example.vitala.app.Screen
import com.example.vitala.app.Tab
import com.example.vitala.ui.theme.VitalaTheme

@Composable
fun VitalaApp(openRequest: String?, onOpenHandled: () -> Unit, vm: AppViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    VitalaTheme(textScale = s.settings.textSizeChoice.scale, highContrast = s.settings.highContrast) {
        if (!s.ready) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@VitalaTheme
        }

        // Ask for permission to show reminders once the person is set up (Android 13+).
        val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(s.profile.onboarded, s.uid) {
            if (s.uid != null && s.profile.onboarded && Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        LaunchedEffect(openRequest, s.uid, s.profile.onboarded) {
            if (openRequest != null && s.uid != null && s.profile.onboarded) { vm.handleOpen(openRequest); onOpenHandled() }
        }

        BackHandler(enabled = vm.canGoBack()) { vm.back() }

        if (s.uid == null) { AuthScreen(s, vm); return@VitalaTheme }
        if (s.loadFailed) {
            Page(
                title = "We couldn't load your data", subtitle = "You are still signed in. Check your connection and try again.",
                actions = { PrimaryButton("Try again", vm::retryLoad, enabled = !s.busy); SecondaryButton("Sign out", vm::signOut) }
            ) { Busy(s.busy); ErrorText(s.error) }
            return@VitalaTheme
        }
        Route(s, vm)
    }
}

@Composable
private fun Route(s: AppState, vm: AppViewModel) {
    val bar: @Composable () -> Unit = { TabBar(s.tab, vm::selectTab) }
    when (val sc = s.screen) {
        is Screen.TabRoot -> when (sc.tab) {
            Tab.HOME -> HomeScreen(s, vm, bar)
            Tab.INSIGHTS -> InsightsScreen(s, vm, bar)
            Tab.PLAN -> PlanScreen(s, vm, bar)
            Tab.SETTINGS -> SettingsScreen(s, vm, bar)
        }
        Screen.Auth -> AuthScreen(s, vm)
        Screen.AboutYou -> AboutYouScreen(s, vm, onboarding = true)
        Screen.EditAccount -> AboutYouScreen(s, vm, onboarding = false)
        Screen.SetupDay -> SetupDayScreen(s, vm)
        is Screen.AddCondition -> AddConditionScreen(s, vm, sc.onboarding)
        is Screen.AddPlan -> AddPlanScreen(s, vm, sc.conditionId, sc.onboarding, update = false)
        is Screen.UpdatePlan -> AddPlanScreen(s, vm, sc.conditionId, onboarding = false, update = true)
        is Screen.ManualPlan -> ManualPlanScreen(s, vm, sc.conditionId, sc.onboarding)
        is Screen.PlanReviewScreen -> PlanReviewScreen(s, vm, sc.conditionId, sc.source, sc.onboarding)
        is Screen.ReminderTimes -> ReminderTimesScreen(s, vm, sc.onboarding, sc.fromSave)
        Screen.OnboardMedicines -> OnboardMedicinesScreen(s, vm)
        Screen.OnboardCalendar -> OnboardCalendarScreen(s, vm)

        is Screen.ExerciseDetail -> ExerciseDetailScreen(s, vm, sc.time)
        Screen.BeforeStart -> BeforeStartScreen(s, vm)
        Screen.SafetyQuestions -> SafetyQuestionsScreen(s, vm)
        Screen.SessionToday -> SessionTodayScreen(s, vm)
        Screen.ChooseLevel -> ChooseLevelScreen(s, vm)
        Screen.Timer -> TimerScreen(s, vm)
        is Screen.ExerciseLog -> ExerciseLogScreen(s, vm)
        Screen.SafetyBlock -> SafetyBlockScreen(s, vm)
        Screen.SuggestedTime -> SuggestedTimeScreen(s, vm)

        is Screen.CheckIn -> CheckInScreen(s, vm, sc.step)
        Screen.CheckInDone -> CheckInDoneScreen(s, vm)
        is Screen.Weekly -> WeeklyScreen(s, vm, sc.step)
        Screen.SomethingChanged -> SomethingChangedScreen(s, vm)

        Screen.InsightsMonth -> InsightsScreen(s, vm, bar)
        is Screen.DayDetail -> DayDetailScreen(s, vm, sc.date)
        is Screen.EditDay -> EditDayScreen(s, vm, sc.date)
        Screen.ExportPdf -> ExportPdfScreen(s, vm)

        is Screen.PlanHistory -> PlanHistoryScreen(s, vm, sc.conditionId)
        is Screen.PlanVersionView -> PlanVersionViewScreen(s, vm, sc.programId)
        is Screen.PlanPhotos -> PlanPhotosScreen(s, vm, sc.programId)
        is Screen.PlanItemDetail -> PlanItemDetailScreen(s, vm, sc.programId, sc.key)

        Screen.Conditions -> ConditionsScreen(s, vm)
        is Screen.EditCondition -> EditConditionScreen(s, vm, sc.id)
        Screen.Medicines -> MedicinesScreen(s, vm)
        is Screen.EditMedicine -> EditMedicineScreen(s, vm, sc.id)
        Screen.Health -> HealthScreen(s, vm)
        Screen.Notifications -> NotificationsScreen(s, vm)
        Screen.Calendar -> CalendarScreen(s, vm)
        Screen.TextSize -> TextSizeScreen(s, vm)
        Screen.Account -> AccountScreen(s, vm)
        Screen.DeleteAccount -> DeleteAccountScreen(s, vm)

        is Screen.MedicineReminder -> MedicineReminderScreen(s, vm, sc.medId, sc.time)
        is Screen.ExerciseReminder -> ExerciseReminderScreen(s, vm, sc.time)
    }
}

/** White bar, selected tab in blue with a 4 dp top rule and bold label. Icons always have a text label. */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val tabs = listOf(
        Triple(Tab.HOME, "Home", Icons.Outlined.Home), Triple(Tab.INSIGHTS, "Insights", Icons.Outlined.BarChart),
        Triple(Tab.PLAN, "Plan", Icons.Outlined.Description), Triple(Tab.SETTINGS, "Settings", Icons.Outlined.Tune)
    )
    Row(Modifier.fillMaxWidth().background(Color.White)) {
        tabs.forEach { (tab, label, icon) ->
            val sel = tab == selected
            Column(
                Modifier.weight(1f).heightIn(min = 64.dp).semantics { role = Role.Tab; this.selected = sel }
                    .clickable { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp).heightIn(min = 4.dp, max = 4.dp).background(if (sel) actionColor() else BorderGreyLine()))
                Icon(icon, null, tint = if (sel) actionColor() else secondaryText())
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) actionColor() else secondaryText(), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun BorderGreyLine() = borderColor()
