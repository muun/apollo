package io.muun.apollo.domain.model

import io.muun.apollo.external.Globals
import io.muun.common.utils.LnInvoice
import io.muun.common.utils.Preconditions
import javax.money.MonetaryAmount

data class PaymentRequest (val type: Type,
                           val amount: MonetaryAmount? = null,
                           val description: String? = null,
                           val contact: Contact? = null,
                           val address: String? = null,
                           val hardwareWallet: HardwareWallet? = null,
                           val invoice: LnInvoice? = null,
                           val swap: SubmarineSwap? = null,
                           val feeInSatoshisPerByte: Double,
                           val takeFeeFromAmount: Boolean = false) {

    enum class Type {
        TO_CONTACT,
        TO_ADDRESS,
        TO_LN_INVOICE,
        TO_HARDWARE_WALLET,
        FROM_HARDWARE_WALLET
    }

    /**
     * Return a cloned PaymentRequest with a new amount and a new description.
     */
    fun withChanges(newAmount: MonetaryAmount,
                    newDesc: String,
                    newFeeInSatoshisPerByte: Double): PaymentRequest {

        return copy(
            amount = newAmount,
            description = newDesc,
            feeInSatoshisPerByte = newFeeInSatoshisPerByte
        )
    }

    fun withFeeRate(newFeeInSatoshisPerByte: Double) =
            copy(feeInSatoshisPerByte = newFeeInSatoshisPerByte)

    fun withTakeFeeFromAmount(takeFeeFromAmount: Boolean) =
        copy(takeFeeFromAmount = takeFeeFromAmount)

    fun toJson() =
            PaymentRequestJson(
                    type,
                    amount,
                    description,
                    contact?.toJson(),
                    address,
                    hardwareWallet?.toJson(),
                    invoice?.original,
                    swap?.toJson(),
                    feeInSatoshisPerByte,
                    takeFeeFromAmount
            )

    companion object {

        /** Create from {@link PaymentRequestJson}*/
        @JvmStatic
        fun fromJson(payReqJson: PaymentRequestJson) =
                PaymentRequest(
                        payReqJson.type!!,
                        payReqJson.amount,
                        payReqJson.description,
                        Contact.fromJson(payReqJson.contact),
                        payReqJson.address,
                        HardwareWallet.fromJson(payReqJson.hardwareWallet),
                        payReqJson.invoice?.let { LnInvoice.decode(Globals.INSTANCE.network, it) },
                        SubmarineSwap.fromJson(payReqJson.swap),
                        payReqJson.feeInSatoshisPerByte,
                        payReqJson.takeFeeFromAmount
                )

        /** Create a PaymentRequest to send money to a contact. */
        @JvmStatic
        fun toContact(contact: Contact,
                      amount: MonetaryAmount,
                      description: String,
                      feeInSatoshisPerByte: Double): PaymentRequest {

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
        fun toAddress(address: String,
                      amount: MonetaryAmount?,
                      description: String?,
                      feeInSatoshisPerByte: Double): PaymentRequest {

            Preconditions.checkNotNull(address)

            return PaymentRequest(
                Type.TO_ADDRESS,
                amount = amount,
                description = description,
                address = address,
                feeInSatoshisPerByte = feeInSatoshisPerByte
            )
        }

        /** Create a PaymentRequest to send money to an address. */
        @JvmStatic
        fun toHardwareWallet(hardwareWallet: HardwareWallet,
                             amount: MonetaryAmount,
                             description: String,
                             feeInSatoshisPerByte: Double): PaymentRequest {

            Preconditions.checkNotNull(hardwareWallet)

            return PaymentRequest(
                Type.TO_HARDWARE_WALLET,
                amount = amount,
                description = description,
                hardwareWallet = hardwareWallet,
                feeInSatoshisPerByte = feeInSatoshisPerByte
            )
        }

        /** Create a PaymentRequest to receive money from a hardware wallet. */
        @JvmStatic
        fun fromHardwareWallet(hardwareWallet: HardwareWallet,
                               amount: MonetaryAmount,
                               description: String,
                               feeInSatoshisPerByte: Double): PaymentRequest {

            Preconditions.checkNotNull(hardwareWallet)

            return PaymentRequest(
                Type.FROM_HARDWARE_WALLET,
                amount = amount,
                description = description,
                hardwareWallet = hardwareWallet,
                feeInSatoshisPerByte = feeInSatoshisPerByte
            )
        }

        /** Create a PaymentRequest to send money to an Invoice. */
        @JvmStatic
        fun toLnInvoice(invoice: LnInvoice,
                        amount: MonetaryAmount,
                        description: String,
                        submarineSwap: SubmarineSwap,
                        feeInSatoshisPerByte: Double): PaymentRequest {

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
