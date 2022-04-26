package io.muun.apollo.presentation.ui.utils

import io.muun.common.utils.Dates.HOUR_IN_SECONDS
import io.muun.common.utils.Dates.MINUTE_IN_SECONDS

class ReceiveLnInvoiceFormatter {

    /**
     * Format using minimalistic pattern/display/formatting instead of provided to better fit in
     * small screen space.
     */
    fun formatSeconds(timeInSeconds: Long): CharSequence {

        val hours = timeInSeconds / HOUR_IN_SECONDS
        val minutes = (timeInSeconds % HOUR_IN_SECONDS) / MINUTE_IN_SECONDS
        val seconds = timeInSeconds % MINUTE_IN_SECONDS

        return String.format("%d:%02d:%02d", hours, minutes, seconds)
    }
}