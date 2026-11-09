package com.example.vitala.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.vitala.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderKind { EXERCISE, MEDICINE, CHECKIN, WEEKLY }

/** One repeating reminder. [time] is when it should ring; [weekday] (ISO 1-7) only for weekly ones. */
data class ReminderSpec(
    val key: String,
    val kind: String,
    val title: String,
    val body: String,
    val time: String,
    val weekday: Int? = null,
    val important: Boolean = false,
    val medId: String? = null,
    val medTime: String? = null,
    val exerciseTime: String? = null,
    /** Hidden text for the lock screen (NT-1) */
    val privateTitle: String? = null
)

data class QuietHours(val on: Boolean, val start: String, val end: String) {
    fun contains(t: LocalTime): Boolean {
        if (!on) return false
        val s = start.toTime(); val e = end.toTime()
        return if (s <= e) t >= s && t < e else t >= s || t < e
    }
}

/**
 * Local notifications scheduled on the device (works offline). Uses inexact-but-prompt alarms so no
 * special "exact alarm" permission is needed. The list is also saved to SharedPreferences so it can
 * be rescheduled after a restart or a time-zone change.
 */
object Reminders {
    private const val PREFS = "reminders"
    private const val KEY_SPECS = "specs"
    private const val KEY_QUIET = "quiet"
    const val ACTION_FIRE = "com.example.vitala.FIRE"
    const val ACTION_TOOK = "com.example.vitala.TOOK"
    const val ACTION_SKIP = "com.example.vitala.SKIP"
    const val ACTION_SNOOZE = "com.example.vitala.SNOOZE"
    const val EXTRA_KEY = "key"
    const val EXTRA_NUDGED = "nudged"
    const val EXTRA_OPEN = "open"
    private val gson = Gson()

    // Channel importance can't be raised once a channel exists, so the high-importance
    // channels (pop up on screen) use new ids and the old default-importance ones are removed.
    private const val CH_EXERCISE = "exercise_alert"
    private const val CH_MEDICINE = "medicine_alert"
    private const val CH_CHECKIN = "checkin_alert"
    private val OLD_CHANNELS = listOf("exercise", "medicine", "checkin")
    private const val TEST_KEY = "test"
    private const val TEST_ID = 7_000_001

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        OLD_CHANNELS.forEach { nm.deleteNotificationChannel(it) }
        listOf(
            CH_EXERCISE to "Exercise reminders", CH_MEDICINE to "Medicine reminders",
            CH_CHECKIN to "Check-in reminders"
        ).forEach { (id, name) ->
            nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH))
        }
    }

    /** False when notifications are blocked for the app (permission denied or turned off in system settings). */
    fun notificationsAllowed(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** False when one of the reminder channels was switched off in system settings. */
    fun channelsAllowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return listOf(CH_EXERCISE, CH_MEDICINE, CH_CHECKIN).all {
            (nm.getNotificationChannel(it)?.importance ?: NotificationManager.IMPORTANCE_HIGH) != NotificationManager.IMPORTANCE_NONE
        }
    }

    /** Exact alarms ring on time; without the permission Android may deliver a reminder up to an hour late. */
    fun canRingOnTime(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Rings at [at] (epoch ms): exactly when allowed, otherwise as close as Android permits. */
    private fun setAlarm(ctx: Context, at: Long, op: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (canRingOnTime(ctx)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op); return
            } catch (_: SecurityException) { /* permission revoked in between: fall back */ }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    /** Shows a sample reminder right now. Returns false if the system blocks it. */
    fun sendTestNow(ctx: Context): Boolean {
        createChannels(ctx)
        if (!notificationsAllowed(ctx)) return false
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, "notifications")
        val pi = PendingIntent.getActivity(ctx, TEST_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH_CHECKIN)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Test reminder")
            .setContentText("Reminders are working. Your real reminders will look like this.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        return try { NotificationManagerCompat.from(ctx).notify(TEST_ID, n); true } catch (_: SecurityException) { false }
    }

    /** Schedules a sample reminder [seconds] from now through the same alarm path as real reminders. */
    fun sendTestLater(ctx: Context, seconds: Int) {
        setAlarm(ctx, System.currentTimeMillis() + seconds * 1000L, pending(ctx, TEST_KEY, ACTION_FIRE))
    }

    fun scheduleAll(ctx: Context, specs: List<ReminderSpec>, quiet: QuietHours) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old: List<ReminderSpec> = loadSpecs(ctx)
        old.forEach { cancel(ctx, it.key) }
        prefs.edit().putString(KEY_SPECS, gson.toJson(specs)).putString(KEY_QUIET, gson.toJson(quiet)).apply()
        specs.forEach { scheduleNext(ctx, it) }
    }

    fun rescheduleFromPrefs(ctx: Context) {
        loadSpecs(ctx).forEach { scheduleNext(ctx, it) }
    }

    fun clear(ctx: Context) = scheduleAll(ctx, emptyList(), QuietHours(false, "21:00", "07:00"))

    private fun loadSpecs(ctx: Context): List<ReminderSpec> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SPECS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<ReminderSpec>>(json, object : TypeToken<List<ReminderSpec>>() {}.type)
        }.getOrNull().orEmpty()
    }

    private fun quiet(ctx: Context): QuietHours =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_QUIET, null)
            ?.let { runCatching { gson.fromJson(it, QuietHours::class.java) }.getOrNull() }
            ?: QuietHours(false, "21:00", "07:00")

    fun nextTrigger(spec: ReminderSpec, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val t = spec.time.toTime()
        var day = now.toLocalDate()
        var at = day.atTime(t)
        if (spec.weekday != null) {
            while (day.dayOfWeek != DayOfWeek.of(spec.weekday) || at <= now) { day = day.plusDays(1); at = day.atTime(t) }
        } else if (at <= now) at = day.plusDays(1).atTime(t)
        return at
    }

    private fun pending(ctx: Context, key: String, action: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_KEY, key).apply(extras)
        return PendingIntent.getBroadcast(ctx, (action + key).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleNext(ctx: Context, spec: ReminderSpec) {
        val at = nextTrigger(spec).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(ctx, at, pending(ctx, spec.key, ACTION_FIRE))
    }

    /** "Remind me in 15 minutes" from inside the app. */
    fun snoozeKey(ctx: Context, key: String) {
        val at = System.currentTimeMillis() + 15 * 60_000L
        setAlarm(ctx, at, pending(ctx, "$key-snooze", ACTION_FIRE))
    }

    private fun cancel(ctx: Context, key: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, key, ACTION_FIRE))
        am.cancel(pending(ctx, "$key-snooze", ACTION_FIRE))
    }

    private fun openIntent(ctx: Context, spec: ReminderSpec, request: Int): PendingIntent {
        val open = when (ReminderKind.valueOf(spec.kind)) {
            ReminderKind.MEDICINE -> "med:${spec.medId}:${spec.medTime}"
            ReminderKind.EXERCISE -> "exercise:${spec.exerciseTime}"
            ReminderKind.CHECKIN -> "checkin"
            ReminderKind.WEEKLY -> "weekly"
        }
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, open)
        return PendingIntent.getActivity(ctx, request, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun handle(ctx: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        if (key == TEST_KEY) { if (intent.action == ACTION_FIRE) sendTestNow(ctx); return }
        val baseKey = key.removeSuffix("-snooze")
        val spec = loadSpecs(ctx).firstOrNull { it.key == baseKey }
        when (intent.action) {
            ACTION_FIRE -> {
                if (spec == null) return
                if (!key.endsWith("-snooze")) scheduleNext(ctx, spec)          // repeat tomorrow / next week
                val q = quiet(ctx)
                if (q.contains(LocalTime.now()) && !spec.important) return        // NT-3
                show(ctx, spec, nudged = key.endsWith("-snooze"))
            }
            ACTION_SNOOZE -> {
                NotificationManagerCompat.from(ctx).cancel(baseKey.hashCode())
                if (spec == null) return
                val at = System.currentTimeMillis() + 15 * 60_000L
                setAlarm(ctx, at, pending(ctx, "$baseKey-snooze", ACTION_FIRE))
                logMed(spec, MedAction.SNOOZED)
            }
            ACTION_TOOK, ACTION_SKIP -> {
                NotificationManagerCompat.from(ctx).cancel(baseKey.hashCode())
                if (spec == null) return
                logMed(spec, if (intent.action == ACTION_TOOK) MedAction.TAKEN else MedAction.SKIPPED)
            }
        }
    }

    private fun logMed(spec: ReminderSpec, action: MedAction) {
        val medId = spec.medId ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ev = MedEvent(medicationId = medId, scheduledTime = spec.medTime, action = action.name)
        UserStore().put(uid, "medEvents", ev.id, ev)
    }

    private fun show(ctx: Context, spec: ReminderSpec, nudged: Boolean) {
        val kind = ReminderKind.valueOf(spec.kind)
        val channel = when (kind) { ReminderKind.EXERCISE -> CH_EXERCISE; ReminderKind.MEDICINE -> CH_MEDICINE; else -> CH_CHECKIN }
        val id = spec.key.hashCode()
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, spec, id))
        if (spec.privateTitle != null) {
            // NT-1: lock screen shows a generic line; the name appears once unlocked.
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            b.setPublicVersion(
                NotificationCompat.Builder(ctx, channel).setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(spec.privateTitle).build()
            )
        }
        val snooze = pending(ctx, spec.key, ACTION_SNOOZE)
        when (kind) {
            ReminderKind.MEDICINE -> {
                b.addAction(0, "I took it", pending(ctx, spec.key, ACTION_TOOK))
                if (!nudged) b.addAction(0, "Remind me in 15 minutes", snooze)   // NT-2: one nudge only
                b.addAction(0, "Skip this time", pending(ctx, spec.key, ACTION_SKIP))
            }
            ReminderKind.EXERCISE -> {
                b.addAction(0, "Start now", openIntent(ctx, spec, id + 1))
                if (!nudged) b.addAction(0, "Remind me in 15 minutes", snooze)
            }
            else -> b.addAction(0, "Open", openIntent(ctx, spec, id + 2))
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (_: SecurityException) { /* permission is off: the Notifications screen shows how to turn it on */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.createChannels(context)
        Reminders.handle(context, intent)
    }
}

/** Puts reminders back after a restart, app update or time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.createChannels(context)
        Reminders.rescheduleFromPrefs(context)
    }
}
