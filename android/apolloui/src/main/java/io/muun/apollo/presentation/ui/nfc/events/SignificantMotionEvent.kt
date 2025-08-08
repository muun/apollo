package io.muun.apollo.presentation.ui.nfc.events

internal data class SignificantMotionEvent(val motion: Float) : ISensorEvent {
    override fun handle(): List<Pair<String, String>> {
        return listOf("significant_motion" to motion.toString())
    }
}