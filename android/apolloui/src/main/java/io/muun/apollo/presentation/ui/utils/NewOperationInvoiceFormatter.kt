package io.muun.apollo.presentation.ui.utils

import android.content.Context
import io.muun.apollo.R

class NewOperationInvoiceFormatter(val context: Context) {

    companion object {
        private const val MINUTE_IN_SECONDS: Long = 60
        private const val HOUR_IN_SECONDS = MINUTE_IN_SECONDS * 60
        private const val DAY_IN_SECONDS = HOUR_IN_SECONDS * 24
        private const val WEEK_IN_SECONDS = DAY_IN_SECONDS * 7
    }

    /**
     * Format verbose wording, tailored for NewOperation screen.
     */
    fun formatSeconds(timeInSeconds: Long): CharSequence {

        val weeks: Long = timeInSeconds / WEEK_IN_SECONDS
        val days: Long = timeInSeconds % WEEK_IN_SECONDS / DAY_IN_SECONDS
        val hours: Long = timeInSeconds % DAY_IN_SECONDS / HOUR_IN_SECONDS
        val mins: Long = timeInSeconds % HOUR_IN_SECONDS / MINUTE_IN_SECONDS
        val secs: Long = timeInSeconds % MINUTE_IN_SECONDS

        return when {
            weeks > 0 -> context.getString(R.string.new_operation_invoice_exp_weeks, weeks, days)
            days > 0 -> context.getString(R.string.new_operation_invoice_exp_days, days, hours)
            hours > 0 -> context.getString(R.string.new_operation_invoice_exp_hours, hours, mins)
            mins > 0 -> context.getString(R.string.new_operation_invoice_exp_minutes, mins, secs)
            else -> context.getString(R.string.new_operation_invoice_exp_seconds, mins, secs)
        }
    }
}