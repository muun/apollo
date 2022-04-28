package io.muun.apollo.presentation.ui.utils

import android.content.Context
import io.muun.apollo.R
import io.muun.common.utils.Dates.HOUR_IN_SECONDS
import io.muun.common.utils.Dates.MINUTE_IN_SECONDS

class ConfirmationTimeFormatter(val context: Context) {

    companion object {
        private val THIRTY_MINUTES_IN_SECONDS = 30 * MINUTE_IN_SECONDS
        private val THREE_HOURS_IN_SECONDS = 3 * HOUR_IN_SECONDS
    }

    fun formatMs(timeMs: Long): CharSequence {
        val seconds = roundConfirmationTimeInSeconds(timeMs / 1000)
        val hours = seconds / HOUR_IN_SECONDS
        val minutes = (seconds % HOUR_IN_SECONDS) / MINUTE_IN_SECONDS

        return if (seconds < HOUR_IN_SECONDS) {
            // Under an hour, just show "X minutes":
            context.getString(R.string.fee_option_item_mins, minutes)

        } else if (seconds < THREE_HOURS_IN_SECONDS) {
            // Under 3 hours, show "X hours Y minutes":
            context.getString(R.string.fee_option_item_hs_mins, hours, minutes)

        } else {
            // Over 3 hours, show "X hours":
            context.getString(R.string.fee_option_item_hs, hours)
        }
    }

    private fun roundConfirmationTimeInSeconds(seconds: Long): Long {
        return if (seconds <= THIRTY_MINUTES_IN_SECONDS) {
            // Never calculate less than 30 minutes:
            THIRTY_MINUTES_IN_SECONDS

        } else if (seconds < THREE_HOURS_IN_SECONDS) {
            // Round up to the nearest 30-minute mark:
            (seconds / THIRTY_MINUTES_IN_SECONDS + 1) * THIRTY_MINUTES_IN_SECONDS

        } else {
            // Round up to the nearest hour mark:
            (seconds / HOUR_IN_SECONDS + 1) * HOUR_IN_SECONDS
        }
    }
}