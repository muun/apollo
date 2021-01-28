package io.muun.apollo.presentation.ui.utils

import android.content.Context
import io.muun.apollo.R

class ConfirmationTimeFormatter(val context: Context) {

    companion object {
        private const val SECONDS_1_MINUTE: Long = 60
        private const val SECONDS_30_MINUTES = SECONDS_1_MINUTE * 30
        private const val SECONDS_1_HOUR = SECONDS_1_MINUTE * 60
        private const val SECONDS_3_HOURS = SECONDS_1_HOUR * 3
    }

    fun formatMs(timeMs: Long): CharSequence {
        val seconds = roundConfirmationTimeInSeconds(timeMs / 1000)
        val hours = seconds / SECONDS_1_HOUR
        val minutes = (seconds % SECONDS_1_HOUR) / SECONDS_1_MINUTE

        return if (seconds < SECONDS_1_HOUR) {
            // Under an hour, just show "X minutes":
            context.getString(R.string.fee_option_item_mins, minutes)

        } else if (seconds < SECONDS_3_HOURS) {
            // Under 3 hours, show "X hours Y minutes":
            context.getString(R.string.fee_option_item_hs_mins, hours, minutes)

        } else {
            // Over 3 hours, show "X hours":
            context.getString(R.string.fee_option_item_hs, hours)
        }
    }

    private fun roundConfirmationTimeInSeconds(seconds: Long): Long {
        return if (seconds <= SECONDS_30_MINUTES) {
            // Never calculate less than 30 minutes:
            SECONDS_30_MINUTES

        } else if (seconds < SECONDS_3_HOURS) {
            // Round up to the nearest 30-minute mark:
            (seconds / SECONDS_30_MINUTES + 1) * SECONDS_30_MINUTES

        } else {
            // Round up to the nearest hour mark:
            (seconds / SECONDS_1_HOUR + 1) * SECONDS_1_HOUR
        }
    }
}