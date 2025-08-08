package io.muun.apollo.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LnUrlWithdraw(val lnUrl: String, val service: String, val invoice: String) {

    companion object {
        fun deserialize(serialization: String): LnUrlWithdraw =
            Json.decodeFromString(serialization)
    }

    fun serialize(): String  =
        Json.encodeToString(this)
}
