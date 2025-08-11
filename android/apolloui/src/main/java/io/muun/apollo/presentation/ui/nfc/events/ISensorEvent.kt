package io.muun.apollo.presentation.ui.nfc.events

/**
 * Represents a generic sensor event that can be handled and transformed into key-value pairs.
 */
interface ISensorEvent {
    /**
     * Processes the sensor event and returns a list of key-value pairs representing event data.
     *
     * @return A list of pairs where each key is a descriptive label and each value is the corresponding sensor reading.
     */
    fun handle(): List<Pair<String, String>>
}