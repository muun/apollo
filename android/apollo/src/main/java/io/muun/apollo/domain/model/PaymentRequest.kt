package io.muun.apollo.domain.model

import io.muun.apollo.domain.libwallet.DecodedInvoice
import io.muun.common.utils.Preconditions

data class PaymentRequest(
    val type: Type,
    val contact: Contact? = null,
    val address: String? = null,
    val swap: SubmarineSwap? = null,
) {

    enum class Type {
        TO_CONTACT,
        TO_ADDRESS,
        TO_LN_INVOICE
    }

    companion object {

        /** Create a PaymentRequest to send money to a contact. */
        @JvmStatic
        fun toContact(
            contact: Contact,
        ): PaymentRequest {

            Preconditions.checkNotNull(contact)

            return PaymentRequest(
                Type.TO_CONTACT,
                contact = contact,
            )
        }

        /** Create a PaymentRequest to send money to an address. */
        @JvmStatic
        fun toAddress(
            address: String,
        ): PaymentRequest {

            Preconditions.checkNotNull(address)

            return PaymentRequest(
                Type.TO_ADDRESS,
                address = address,
            )
        }

        /** Create a PaymentRequest to send money to an Invoice. */
        @JvmStatic
        fun toLnInvoice(
            invoice: DecodedInvoice,
            submarineSwap: SubmarineSwap,
        ): PaymentRequest {

            Preconditions.checkNotNull(invoice)

            return PaymentRequest(
                Type.TO_LN_INVOICE,
                swap = submarineSwap,
            )
        }
    }
}
