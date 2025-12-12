package com.example.alwaysondashboard.data

import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean
)

class CalendarRepository(private val context: Context) {

    suspend fun todaysEvents(): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val now = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val startMillis = now.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY
            )
            val selection = "(${CalendarContract.Events.DTSTART} < ?) AND (${CalendarContract.Events.DTEND} > ?)"
            val selectionArgs = arrayOf(endMillis.toString(), startMillis.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            val events = mutableListOf<CalendarEvent>()
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)

                while (cursor.moveToNext()) {
                    events.add(
                        CalendarEvent(
                            id = cursor.getLong(idIdx),
                            title = cursor.getString(titleIdx) ?: "(No title)",
                            startMillis = cursor.getLong(startIdx),
                            endMillis = cursor.getLong(endIdx),
                            allDay = cursor.getInt(allDayIdx) == 1
                        )
                    )
                }
            }

            events
        }
    }
}
