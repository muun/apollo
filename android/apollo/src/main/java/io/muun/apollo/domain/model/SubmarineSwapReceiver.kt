package io.muun.apollo.domain.model

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.muun.common.api.SubmarineSwapReceiverJson
import java.io.IOException
import java.util.*

class SubmarineSwapReceiver(
    val alias: String?,
    val serializedNetworkAddresses: String,
    val publicKey: String
) {

    companion object {

        private val JSON_MAPPER = ObjectMapper()

        fun fromJson(receiver: SubmarineSwapReceiverJson): SubmarineSwapReceiver {
            try {
                return SubmarineSwapReceiver(
                    receiver.alias,
                    JSON_MAPPER.writeValueAsString(receiver.networkAddresses),
                    receiver.publicKey
                )
            } catch (e: JsonProcessingException) {
                throw RuntimeException(e)
            }
        }
    }

    val formattedDestination by lazy {
        if (displayNetworkAddress.isNotBlank()) {
            "$publicKey@$displayNetworkAddress"
        } else {
            publicKey
        }
    }

    val displayNetworkAddress by lazy {
        if (networkAddresses.isEmpty()) "" else networkAddresses[0]
    }

    /**
     * Get the list of network addresses, ie. the concatenation of "{host}:{port}". Might be empty!
     */
    private val networkAddresses by lazy {
        try {
            listOf(*JSON_MAPPER.readValue(serializedNetworkAddresses, Array<String>::class.java))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun toJson(): SubmarineSwapReceiverJson {
        return SubmarineSwapReceiverJson(
            alias,
            networkAddresses,
            publicKey
        )
    }

    /**
     * Adapt apollo's (java) model to libwallet's (go).
     */
    fun toLibwallet(): newop.SubmarineSwapReceiver {
        val libwalletSwapReceiver = newop.SubmarineSwapReceiver()
        libwalletSwapReceiver.alias = alias ?: ""
        libwalletSwapReceiver.publicKey = publicKey
        libwalletSwapReceiver.networkAddresses = networkAddresses.joinToString { "\n" }
        return libwalletSwapReceiver
    }
}
