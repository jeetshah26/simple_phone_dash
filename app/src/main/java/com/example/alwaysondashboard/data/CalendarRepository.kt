package com.example.alwaysondashboard.data

import android.content.Context
import android.content.ContentUris
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
            queryEvents(startMillis, endMillis)
        }
    }

    suspend fun upcomingEvents(daysAhead: Long = 7): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val now = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val startMillis = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = now.plusDays(daysAhead + 1).atStartOfDay(zone).toInstant().toEpochMilli()
            queryEvents(startMillis, endMillis)
        }
    }

    private fun queryEvents(startMillis: Long, endMillis: Long): List<CalendarEvent> {
        // Instances works better on older devices and handles recurring events.
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        val events = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            builder.build(),
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

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

        return events.sortedBy { it.startMillis }
    }
}
