package io.muun.apollo.domain.model

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.muun.common.api.SubmarineSwapReceiverJson
import java.io.IOException
import java.util.*

class SubmarineSwapReceiver(
        val alias: String?,
        val serializedNetworkAddresses: String,
        val publicKey: String) {

    val formattedDestination: String
        get() = "$publicKey@$displayNetworkAddress"

    val displayNetworkAddress: String
        get() {
            val networkAddresses = networkAddresses
            return if (networkAddresses.isEmpty()) "" else networkAddresses[0]
        }

    /**
     * Get the list of network addresses, ie. the concatenation of "{host}:{port}". Might be empty!
     */
    private val networkAddresses: List<String>
        get() {

            try {
                return Arrays.asList(*JSON_MAPPER.readValue(
                        serializedNetworkAddresses,
                        Array<String>::class.java
                ))
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
}
