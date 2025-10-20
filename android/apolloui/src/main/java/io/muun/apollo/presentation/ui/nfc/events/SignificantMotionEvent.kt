package io.muun.apollo.presentation.ui.nfc.events

import io.muun.apollo.domain.model.SensorEvent
import org.threeten.bp.ZonedDateTime

/**
 * Represents a significant motion sensor event, capturing abrupt device movements.
 */
internal data class SignificantMotionEvent(
    override val id: Long,
    override val eventType: String = "significant_motion",
    val motion: Float,
) : ISensorEvent {

    override val timestamp: ZonedDateTime = ZonedDateTime.now()

    override fun handle(): SensorEvent {
        return SensorEvent(
            eventId = id,
            eventType = eventType,
            eventTimestamp = timestamp,
            eventData = mapOf(
                "significant_motion" to motion.toString(),
            )
        )
    }
}