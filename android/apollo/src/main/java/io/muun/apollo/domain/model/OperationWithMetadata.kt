package io.muun.apollo.domain.model

import io.muun.common.crypto.hd.MuunAddress
import io.muun.common.model.OperationDirection
import io.muun.common.model.OperationStatus
import org.threeten.bp.ZonedDateTime

data class OperationWithMetadata(
    val id: Long,
    val direction: OperationDirection?,
    val isExternal: Boolean,
    val senderProfile: PublicProfile?,
    val senderIsExternal: Boolean,
    val receiverProfile: PublicProfile?,
    val receiverIsExternal: Boolean,
    val receiverAddress: String?,
    val receiverAddressDerivationPath: String?,
    val changeAddress: MuunAddress?,
    val amount: BitcoinAmount,
    val fee: BitcoinAmount,
    val confirmations: Long,
    val hash: String?,
    val description: String?,
    val status: OperationStatus,
    val creationDate: ZonedDateTime,
    val exchangeRateWindowHid: Long,
    val swap: SubmarineSwap?,
    val receiverMetadata: String?,
    val senderMetadata: String?,
    val incomingSwap: IncomingSwap?,
    val isRbf: Boolean
)