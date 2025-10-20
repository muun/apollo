package io.muun.apollo.domain.model

import org.threeten.bp.ZonedDateTime

data class SensorEvent(
    val eventId: Long,
    val eventTimestamp: ZonedDateTime,
    val eventType: String,
    val eventData: Map<String, Any>,
)