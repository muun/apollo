package io.muun.apollo.domain.model

import io.muun.common.crypto.hd.MuunAddress
import io.muun.common.model.OperationDirection
import io.muun.common.model.OperationStatus
import org.threeten.bp.ZonedDateTime
import javax.validation.constraints.NotNull

data class OperationWithMetadata(
        val id: Long,

        val direction: OperationDirection? = null,

        @NotNull
        val isExternal: Boolean? = null,

        val senderProfile: PublicProfile? = null,

        @NotNull
        val senderIsExternal: Boolean? = null,

        val receiverProfile: PublicProfile? = null,

        @NotNull
        val receiverIsExternal: Boolean? = null,

        val receiverAddress: String? = null,

        val receiverAddressDerivationPath: String? = null,

        val changeAddress: MuunAddress? = null,

        @NotNull
        val amount: BitcoinAmount? = null,

        @NotNull
        val fee: BitcoinAmount? = null,

        @NotNull
        val confirmations: Long? = null,

        val hash: String? = null,

        val description: String? = null,

        @NotNull
        val status: OperationStatus? = null,

        @NotNull
        val creationDate: ZonedDateTime? = null,

        @NotNull
        val exchangeRateWindowHid: Long? = null,

        val swap: SubmarineSwap? = null,

        val receiverMetadata: String?,

        val senderMetadata: String?,

        val incomingSwap: IncomingSwap? = null,

        val isRbf: Boolean
)