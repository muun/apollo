package io.muun.apollo.domain.model

import kotlinx.serialization.Serializable

@Serializable
class BackgroundEvent(
    val beginTimeInMillis: Long,
    val durationInMillis: Long,
)