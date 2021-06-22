package io.muun.apollo.domain.model

import androidx.annotation.VisibleForTesting
import io.muun.apollo.domain.model.base.HoustonIdModel
import io.muun.apollo.domain.utils.DateUtils
import io.muun.common.api.OperationMetadataJson
import io.muun.common.crypto.hd.MuunAddress
import io.muun.common.model.OperationDirection
import io.muun.common.model.OperationStatus
import io.muun.common.utils.Preconditions
import org.threeten.bp.ZonedDateTime
import javax.money.MonetaryAmount

class Operation(
    id: Long?,
    hid: Long,
    // Because for outgoing ops clients await Houston's to see if its outgoing or cyclical
    @JvmField var direction: OperationDirection?,
    @JvmField val isExternal: Boolean,
    @JvmField val senderProfile: PublicProfile?,
    @JvmField val senderIsExternal: Boolean,
    @JvmField var receiverProfile: PublicProfile?,
    @JvmField var receiverIsExternal: Boolean,
    @JvmField var receiverAddress: String?,
    @JvmField var receiverAddressDerivationPath: String?,
    @JvmField val amount: BitcoinAmount,
    @JvmField val fee: BitcoinAmount,
    @JvmField val confirmations: Long,
    @JvmField var hash: String?,
    @JvmField val description: String?,
    @JvmField val metadata: OperationMetadataJson?,
    @JvmField var status: OperationStatus,
    @JvmField val creationDate: ZonedDateTime,
    @JvmField val exchangeRateWindowHid: Long,
    @JvmField val swap: SubmarineSwap?,
    @JvmField val incomingSwap: IncomingSwap?,
    @JvmField val isRbf: Boolean
) : HoustonIdModel(id, hid) {

    companion object {

        @VisibleForTesting
        const val NO_HID: Long = -1

        private val NO_HASH: String? = null

        @JvmField
        val PENDING_STATUS = listOf(
            OperationStatus.CREATED, OperationStatus.SIGNING, OperationStatus.SIGNED,
            OperationStatus.BROADCASTED, OperationStatus.SWAP_PENDING, OperationStatus.SWAP_ROUTING,
            OperationStatus.SWAP_OPENING_CHANNEL, OperationStatus.SWAP_WAITING_CHANNEL
        )

        /**
         * Create a new Operation, with default values for Houston-generated fields.
         */
        @JvmStatic
        fun createOutgoing(
            myProfile: PublicProfile?,
            receiverProfile: PublicProfile?,
            address: String?,
            addressDerivationPath: String?,
            preparedPayment: PreparedPayment
        ): Operation {
            return Operation(
                null,
                NO_HID,
                null,
                myProfile == null || receiverProfile == null,
                myProfile,
                false,
                receiverProfile,
                receiverProfile == null,
                address,
                addressDerivationPath,
                preparedPayment.amount,
                preparedPayment.fee,
                0L,
                NO_HASH,
                preparedPayment.description,
                OperationMetadataJson(preparedPayment.description),
                OperationStatus.CREATED,
                ZonedDateTime.now(),
                preparedPayment.rateWindowHid,
                null,
                null,
                false // will be defined and updated by Houston
            )
        }

        /**
         * Create a new Submarine Swap Operation.
         */
        @JvmStatic
        fun createSwap(myProfile: PublicProfile?, pp: PreparedPayment, swap: SubmarineSwap) =
            Operation(
                null,
                NO_HID,
                OperationDirection.OUTGOING,
                true,
                myProfile,
                false,
                null,
                true,
                swap.fundingOutput.outputAddress,
                null,
                pp.amount,
                pp.fee,
                0L,
                NO_HASH,
                pp.description,
                OperationMetadataJson(pp.description),
                OperationStatus.CREATED,
                DateUtils.now(),
                pp.rateWindowHid,
                swap,
                null,
                false // will be defined and updated by Houston
            )
    }

    @JvmField
    var changeAddress: MuunAddress? = null

    /**
     * Whether this operation was sent and received by the same User.
     */
    val isCyclical: Boolean
        get() = direction == OperationDirection.CYCLICAL

    /**
     * Whether this operation was received by the current User.
     */
    val isIncoming: Boolean
        get() = direction == OperationDirection.INCOMING

    /**
     * Whether this operation was sent by the current User.
     */
    val isOutgoing: Boolean
        get() = direction == OperationDirection.OUTGOING

    /**
     * Whether this operation is considered pending for UI purposes.
     */
    val isPending: Boolean
        get() = PENDING_STATUS.contains(status)

    /**
     * Whether this operation is considered completed for UI purposes.
     */
    val isCompleted: Boolean
        get() = when (status) {
            OperationStatus.CONFIRMED,
            OperationStatus.SETTLED,
            OperationStatus.SWAP_PAYED -> true

            else -> false
        }

    /**
     * Whether this operation is considered failed for UI purposes.
     */
    val isFailed: Boolean
        get() = when (status) {
            OperationStatus.DROPPED,
            OperationStatus.FAILED,
            OperationStatus.SWAP_FAILED,
            OperationStatus.SWAP_EXPIRED -> true

            else -> false
        }

    val isLendingSwap: Boolean
        get() = swap != null && swap.isLend

    val isIncomingSwap: Boolean
        get() = incomingSwap != null

    /**
     * Get the creation date of the operation, localized to the system timezone.
     */
    val localizedCreationDate: ZonedDateTime
        get() = DateUtils.toSystemDefault(creationDate)

    val total: MonetaryAmount
        get() = amount.inInputCurrency.add(fee.inInputCurrency)

    /**
     * Merge this Operation with an updated copy, choosing whether to replace each field.
     */
    fun mergeWithUpdate(other: Operation): Operation {
        Preconditions.checkArgument(other.id == null || id == other.id)

        return this.apply {
            hid = other.hid
            direction = other.direction
            receiverProfile = other.receiverProfile
            receiverIsExternal = other.receiverIsExternal
            receiverAddress = other.receiverAddress
            receiverAddressDerivationPath = other.receiverAddressDerivationPath
            status = other.status
        }
    }
}