package com.example.vitala.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

data class Window(val start: LocalTime, val end: LocalTime)

data class TimeSuggestion(
    val usual: LocalTime,
    val usualBusy: Boolean,
    val suggested: LocalTime?,
    val freeFrom: LocalTime?,
    val freeTo: LocalTime?,
    val others: List<LocalTime>
)

/**
 * Reads ONLY when the person is busy or free (event start and end). Titles, attendees, locations
 * and notes are never queried, stored or sent anywhere (CAL-2). Everything runs on the device.
 * Google Calendar and Outlook are read through the phone's calendar if the account is added to the phone.
 */
object CalendarAvailability {

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Busy windows on [date]; null when the calendar cannot be read (CAL-6). */
    fun busy(ctx: Context, date: LocalDate): List<Window>? {
        if (!hasPermission(ctx)) return null
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return runCatching {
            val out = mutableListOf<Window>()
            val projection = arrayOf(
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.AVAILABILITY
            )
            CalendarContract.Instances.query(ctx.contentResolver, projection, from, to).use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(2) == 1) continue                                             // all-day: not a time block
                    if (c.getInt(3) == CalendarContract.Events.AVAILABILITY_FREE) continue
                    val s = Instant.ofEpochMilli(maxOf(c.getLong(0), from)).atZone(zone).toLocalTime()
                    val e = Instant.ofEpochMilli(minOf(c.getLong(1), to - 1)).atZone(zone).toLocalTime()
                    if (e > s) out += Window(s, e)
                }
            }
            out
        }.getOrNull()
    }

    /**
     * CAL-3: the free window closest to the time the person set, outside quiet hours, long enough
     * for the session, not overlapping a scheduled medicine time.
     */
    fun suggest(
        busy: List<Window>, usual: LocalTime, minutes: Int, quiet: QuietHours, medTimes: List<LocalTime>
    ): TimeSuggestion {
        val len = maxOf(minutes, 1).toLong()
        fun ok(t: LocalTime): Boolean {
            val end = t.plusMinutes(len)
            if (end <= t) return false                                      // would pass midnight
            if (busy.any { t < it.end && end > it.start }) return false
            if (quiet.contains(t) || quiet.contains(end.minusMinutes(1))) return false
            if (medTimes.any { it >= t && it < end }) return false
            return true
        }
        val usualBusy = !ok(usual) && busy.any { usual < it.end && usual.plusMinutes(len) > it.start }
        val candidates = (0 until 24 * 4).map { LocalTime.of(0, 0).plusMinutes(it * 15L) }.filter(::ok)
            .sortedBy { abs(it.toSecondOfDay() - usual.toSecondOfDay()) }
        val best = candidates.firstOrNull()
        val others = mutableListOf<LocalTime>()
        for (c in candidates.drop(1)) {
            if (others.size >= 3) break
            if (best != null && abs(c.toSecondOfDay() - best.toSecondOfDay()) < 1800) continue
            if (others.none { abs(it.toSecondOfDay() - c.toSecondOfDay()) < 1800 }) others += c
        }
        // The free stretch around the suggestion, for plain wording ("You look free from 11:00 to 12:00").
        var freeFrom: LocalTime? = null
        var freeTo: LocalTime? = null
        if (best != null) {
            var a: LocalTime = best
            while (a > LocalTime.MIN && ok(a.minusMinutes(15))) a = a.minusMinutes(15)
            var b: LocalTime = best
            while (b < LocalTime.of(23, 30) && ok(b.plusMinutes(15))) b = b.plusMinutes(15)
            freeFrom = a; freeTo = b.plusMinutes(len)
        }
        return TimeSuggestion(usual, usualBusy, best, freeFrom, freeTo, others)
    }
}
