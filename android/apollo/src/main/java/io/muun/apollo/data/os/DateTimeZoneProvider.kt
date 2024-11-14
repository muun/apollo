package io.muun.apollo.data.os

import android.content.Context
import android.provider.Settings
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

class DateTimeZoneProvider @Inject constructor(private val context: Context) {

    val autoTimeZone: Int
        get() {
            return Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AUTO_TIME_ZONE,
                -1
            )
        }

    val autoDateTime: Int
        get() {
            return Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AUTO_TIME,
                -1
            )
        }

    val timeZoneId: String
        get() {
            return TimeZone.getDefault().id.take(100)
        }

    val timeZoneOffsetSeconds: Long
        get() {
            return TimeZone.getDefault().rawOffset / 1000L
        }

    val calendarIdentifier: String
        get() {
            if (OS.supportsCalendarType()) {
                val calendar = Calendar.getInstance()
                return calendar.calendarType
            }
            return Constants.UNKNOWN
        }
}