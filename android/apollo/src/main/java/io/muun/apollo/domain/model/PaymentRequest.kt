package io.muun.apollo.domain.model

import io.muun.apollo.domain.libwallet.DecodedInvoice
import io.muun.common.utils.Preconditions
import javax.money.MonetaryAmount

data class PaymentRequest(
    val type: Type,
    val amount: MonetaryAmount? = null,
    val description: String? = null,
    val contact: Contact? = null,
    val address: String? = null,
    val invoice: DecodedInvoice? = null,
    val swap: SubmarineSwap? = null,
    val feeInSatoshisPerByte: Double?, //initially null for AmountLess Invoice
    val takeFeeFromAmount: Boolean = false,
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
            amount: MonetaryAmount,
            description: String,
            feeInSatoshisPerByte: Double,
        ): PaymentRequest {

            Preconditions.checkNotNull(contact)

            return PaymentRequest(
                Type.TO_CONTACT,
                amount = amount,
                description = description,
                contact = contact,
                feeInSatoshisPerByte = feeInSatoshisPerByte
            )
        }

        /** Create a PaymentRequest to send money to an address. */
        @JvmStatic
        fun toAddress(
            address: String,
            amount: MonetaryAmount?,
            description: String?,
            feeInSatoshisPerByte: Double,
        ): PaymentRequest {

            Preconditions.checkNotNull(address)

            return PaymentRequest(
                Type.TO_ADDRESS,
                amount = amount,
                description = description,
                address = address,
                feeInSatoshisPerByte = feeInSatoshisPerByte
            )
        }

        /** Create a PaymentRequest to send money to an Invoice. */
        @JvmStatic
        fun toLnInvoice(
            invoice: DecodedInvoice,
            amount: MonetaryAmount?,
            description: String,
            submarineSwap: SubmarineSwap,
            feeInSatoshisPerByte: Double?,
        ): PaymentRequest {

            Preconditions.checkNotNull(invoice)

            return PaymentRequest(
                Type.TO_LN_INVOICE,
                amount = amount,
                description = description,
                invoice = invoice,
                swap = submarineSwap,
                feeInSatoshisPerByte = feeInSatoshisPerByte
            )
        }
    }
}
