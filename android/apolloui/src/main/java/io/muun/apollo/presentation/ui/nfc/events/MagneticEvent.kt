package io.muun.apollo.presentation.ui.nfc.events

internal data class MagneticEvent(val magnetic: Float) : ISensorEvent {
    override fun handle(): List<Pair<String, String>> {
        return listOf("magnetic_field" to magnetic.toString())
    }
}