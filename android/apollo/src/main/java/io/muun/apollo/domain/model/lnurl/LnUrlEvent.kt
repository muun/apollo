package io.muun.apollo.domain.model.lnurl

import kotlinx.serialization.Serializable

@Serializable // Needed for LnUrlError to be @Serializable
data class LnUrlEvent(val code: Int, val message: String, val metadata: String) {

    val truncatedMessage: String
        get() = if (message.length > 280) {
            message.substring(0, 280)
        } else {
            message
        }
}