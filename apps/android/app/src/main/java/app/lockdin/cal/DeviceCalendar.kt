// Reads the calendars synced to this phone (Google Calendar and others) through Android's
// calendar provider (roadmap step 4, C). Read-only: Lockd In never adds, changes or deletes
// calendar events. Needs READ_CALENDAR, which the person grants in Settings › Calendar.
package app.lockdin.cal

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import app.lockdin.engine.CalEvent

data class DeviceCal(val id: Long, val name: String, val account: String, val isGoogle: Boolean)

object DeviceCalendar {
    fun permitted(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Visible calendars on the phone. Empty without permission. */
    fun calendars(ctx: Context): List<DeviceCal> {
        if (!permitted(ctx)) return emptyList()
        val cols = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.VISIBLE,
        )
        return runCatching {
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(4) == 0) continue
                        add(DeviceCal(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "", c.getString(3) == "com.google"))
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Event instances (recurring events expanded) between `from` and `to`, skipping hidden calendars. */
    fun events(ctx: Context, from: Long, to: Long, hidden: Set<Long>): List<CalEvent> {
        if (!permitted(ctx)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from); ContentUris.appendId(it, to)
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.AVAILABILITY,
            CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.STATUS,
        )
        return runCatching {
            ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getLong(6) in hidden) continue
                        if (c.getInt(7) == CalendarContract.Instances.STATUS_CANCELED) continue
                        add(CalEvent(
                            c.getLong(0), c.getString(1)?.take(80) ?: "Busy", c.getLong(2), c.getLong(3),
                            allDay = c.getInt(4) == 1, busy = c.getInt(5) != CalendarContract.Events.AVAILABILITY_FREE,
                        ))
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
