package io.muun.apollo.presentation.ui.nfc.events

import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime

internal data class AppEvent(
    override val id: Long,
    override val eventType: String = "app",
    val action: String,
) : ISensorEvent {

    override val timestamp: ZonedDateTime = ZonedDateTime.now()

    override fun handle(): SensorEvent {
        return SensorEvent(
            eventId = id,
            eventType = eventType,
            eventTimestamp = timestamp,
            eventData = mapOf(
                "app_action" to action,
            )
        )
    }
}