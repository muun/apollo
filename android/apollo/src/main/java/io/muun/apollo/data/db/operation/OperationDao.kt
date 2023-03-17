package io.muun.apollo.data.db.operation

import io.muun.apollo.data.db.base.HoustonIdDao
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.IncomingSwap
import io.muun.apollo.domain.model.IncomingSwapHtlc
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.model.PublicProfile
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.domain.model.SubmarineSwapFees
import io.muun.apollo.domain.model.SubmarineSwapFundingOutput
import io.muun.apollo.domain.model.SubmarineSwapReceiver
import io.muun.apollo.domain.selector.UtxoSetStateSelector.UtxoSetState
import io.muun.common.Optional
import io.muun.common.api.OperationMetadataJson
import io.muun.common.crypto.hd.MuunAddress
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.model.DebtType
import io.muun.common.model.OperationDirection
import io.muun.common.model.OperationStatus
import io.muun.common.utils.Encodings
import io.muun.common.utils.Pair
import org.threeten.bp.ZonedDateTime
import rx.Completable
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton
import javax.money.MonetaryAmount

@Singleton
open class OperationDao @Inject constructor() : HoustonIdDao<Operation>("operations") {

    override fun deleteAll(): Completable {
        return Completable.fromAction { delightDb.operationQueries.deleteAll() }
    }

    override fun storeUnsafe(op: Operation) {
        delightDb.operationQueries.insertOperation(
            op.id,
            op.hid,
            op.direction!!,
            op.isExternal,
            if (op.senderProfile != null) op.senderProfile.hid else null,
            op.senderIsExternal,
            if (op.receiverProfile != null) op.receiverProfile!!.hid else null,
            op.receiverIsExternal,
            op.receiverAddress,
            op.receiverAddressDerivationPath,
            op.amount.inSatoshis,
            op.amount.inInputCurrency,
            op.amount.inPrimaryCurrency,
            op.fee.inSatoshis,
            op.fee.inInputCurrency,
            op.fee.inPrimaryCurrency,
            op.confirmations,
            op.hash,
            op.description,
            op.status,
            op.creationDate,
            op.exchangeRateWindowHid,
            if (op.swap != null) op.swap.houstonUuid else null,
            if (op.incomingSwap != null) op.incomingSwap.houstonUuid else null,
            op.isRbf,
            op.metadata
        )
    }

    /**
     * Update the operations status and confirmations.
     */
    fun updateStatus(
        operationHid: Long,
        confirmations: Long,
        hash: String?,
        status: OperationStatus,
    ) {
        delightDb.operationQueries.updateStatus(
            confirmations, hash, status, operationHid
        )
    }

    /**
     * Fetches all operations from the db.
     */
    fun fetchAll(): Observable<List<Operation>> {
        return fetchList(delightDb.operationQueries.selectAll(this::fromAllFields))
    }

    /**
     * Fetches a single operation by its id.
     */
    fun fetchById(operationId: Long): Observable<Operation> {
        return fetchOneOrFail(
            delightDb.operationQueries.selectById(
                operationId,
                this::fromAllFields
            )
        )
            .doOnError { error: Throwable -> enhanceError(error, operationId.toString()) }
    }

    /**
     * Fetches a single operation by its Houston id.
     */
    fun fetchByHid(operationHid: Long): Observable<Operation> {
        return fetchOneOrFail(
            delightDb.operationQueries.selectByHid(
                operationHid,
                this::fromAllFields
            )
        )
            .doOnError { error: Throwable -> enhanceError(error, operationHid.toString()) }
    }

    fun fetchLatest(): Observable<Operation> {
        return fetchOneOrFail(delightDb.operationQueries.selectLatest(this::fromAllFields))
            .doOnError { error: Throwable -> enhanceError(error, "null (latest)") }
    }

    fun fetchMaybeLatest(): Observable<Optional<Operation>> {
        return fetchMaybeOne(delightDb.operationQueries.selectLatest(this::fromAllFields))
    }

    fun fetchUnsettled(): Observable<List<Operation>> {
        return fetchList(delightDb.operationQueries.selectUnsettled(this::fromAllFields))
    }

    open fun fetchByIncomingSwapUuid(incomingSwap: String): Observable<Operation> {
        return fetchOneOrFail(
            delightDb.operationQueries.selectByIncomingSwap(
                incomingSwap,
                this::fromAllFields
            )
        )
    }

    fun watchUtxoSetState(): Observable<UtxoSetState> {
        val hasRbfQuery = delightDb.operationQueries.countPendingOps(
            OperationDirection.INCOMING, true, Operation.PENDING_STATUS
        )

        val hasNonRbfQuery = delightDb.operationQueries.countPendingOps(
            OperationDirection.INCOMING, false, Operation.PENDING_STATUS
        )

        return Observable.zip(
            executeCount(hasRbfQuery),
            executeCount(hasNonRbfQuery)
        ) { f, s -> Pair.of(f, s) }
            .map { pair ->
                if (pair.fst > 0) {
                    return@map UtxoSetState.RBF
                } else if (pair.snd > 0) {
                    return@map UtxoSetState.PENDING
                } else {
                    return@map UtxoSetState.CONFIRMED
                }
            }
    }

    private fun fromAllFields(
        id: Long,
        hid: Long,
        direction: OperationDirection,
        isExternal: Boolean,
        _senderHid: Long?,
        senderIsExternal: Boolean,
        _receiverHid: Long?,
        receiverIsExternal: Boolean,
        receiverAddress: String?,
        receiverAddressDerivationPath: String?,
        amountInSatoshis: Long,
        amountInInputCurrency: MonetaryAmount,
        amountInPrimaryCurrency: MonetaryAmount,
        feeInSatoshis: Long,
        feeInInputCurrency: MonetaryAmount,
        feeInPrimaryCurrency: MonetaryAmount,
        confirmations: Long,
        hash: String?,
        description: String?,
        status: OperationStatus,
        creationDate: ZonedDateTime,
        exchangeRateWindowHid: Long,
        _submarineSwapHoustonUuid: String?,
        _incomingSwapHoustonUuid: String?,
        isRbf: Boolean,
        metadata: OperationMetadataJson?,
        // Sender profile
        senderProfileId: Long?,
        senderProfileHid: Long?,
        senderProfileFirstName: String?,
        senderProfileLastName: String?,
        senderProfilePictureUrl: String?,
        // Receiver profile
        receiverProfileId: Long?,
        receiverProfileHid: Long?,
        receiverProfileFirstName: String?,
        receiverProfileLastName: String?,
        receiverProfilePictureUrl: String?,
        // Swap
        swapId: Long?,
        swapUuid: String?,
        swapInvoice: String?,
        swapReceiverAlias: String?,
        swapReceiverNetworkAddresses: String?,
        swapReceiverPublicKey: String?,
        fundingOutputAddress: String?,
        fundingOutputAmountInSatoshis: Long?,
        fundingOutputConfirmationsNeeded: Long?,
        fundingOutputUserLockTime: Long?,
        fundingOutputUserRefundAddress: String?,
        fundingOutputUserRefundAddressPath: String?,
        fundingOutputUserRefundAddressVersion: Long?,
        fundingOutputServerPaymentHashInHex: String?,
        fundingOutputServerPublicKeyInHex: String?,
        swapSweepFeeInSatoshis: Long?,
        swapLightningFeeInSatoshis: Long?,
        swapExpiresAt: ZonedDateTime?,
        swapPayedAt: ZonedDateTime?,
        swapPreimageInHex: String?,
        fundingOutputScriptVersion: Long?,
        fundingOutputExpirationInBlocks: Long?,
        fundingOutputUserPublicKey: String?,
        fundingOutputUserPublicKeyPath: String?,
        fundingOutputMuunPublicKey: String?,
        fundingOutputMuunPublicKeyPath: String?,
        fundingOutputDebtType: DebtType?,
        fundingOutputDebtAmountInSatoshis: Long?,
        // Incoming swap
        incomingSwapId: Long?,
        incomingSwapUuid: String?,
        incomingSwapPaymentHashInHex: String?,
        incomingSwapSphinxPacketInHex: String?,
        incomingSwapCollectInSatoshis: Long?,
        incomingSwapPaymentAmountInSatoshis: Long?,
        incomingSwapPreimageInHex: String?,
        // HTLC
        htlcId: Long?,
        htlcUuid: String?,
        htlcExpirationHeight: Long?,
        htlcFulfillmentFeeSubsidyInSatoshis: Long?,
        htlcLentInSatoshis: Long?,
        htlcSwapServerPublicKeyInHex: String?,
        htlcFulfillmentTxInHex: String?,
        htlcAddress: String?,
        htlcOutputAmountInSatoshis: Long?,
        htlcTxInHex: String?,
        _htlcIncomingSwapHoustonUuid: String?,
    ): Operation {

        val senderProfile = if (senderProfileId != null) {
            PublicProfile(
                senderProfileId,
                checkNotNull(senderProfileHid),
                senderProfileFirstName,
                senderProfileLastName,
                senderProfilePictureUrl
            )
        } else {
            null
        }

        val receiverProfile = if (receiverProfileId != null) {
            PublicProfile(
                receiverProfileId,
                checkNotNull(receiverProfileHid),
                receiverProfileFirstName,
                receiverProfileLastName,
                receiverProfilePictureUrl
            )
        } else {
            null
        }
        val swap: SubmarineSwap?
        if (swapId != null) {
            val receiver = SubmarineSwapReceiver(
                swapReceiverAlias,
                checkNotNull(swapReceiverNetworkAddresses),
                checkNotNull(swapReceiverPublicKey)
            )

            val userPublicKey = if (fundingOutputUserPublicKey != null) {
                PublicKey.deserializeFromBase58(
                    checkNotNull(fundingOutputUserPublicKeyPath),
                    fundingOutputUserPublicKey
                )
            } else {
                null
            }

            val muunPublicKey = if (fundingOutputMuunPublicKey != null) {
                PublicKey.deserializeFromBase58(
                    checkNotNull(fundingOutputMuunPublicKeyPath),
                    fundingOutputMuunPublicKey
                )
            } else {
                null
            }

            val fundingOutput = SubmarineSwapFundingOutput(
                checkNotNull(fundingOutputAddress),
                fundingOutputAmountInSatoshis,
                fundingOutputDebtType,
                fundingOutputDebtAmountInSatoshis,
                fundingOutputConfirmationsNeeded?.toInt(),
                fundingOutputUserLockTime?.toInt(),
                MuunAddress(
                    checkNotNull(fundingOutputUserRefundAddressVersion).toInt(),
                    checkNotNull(fundingOutputUserRefundAddressPath),
                    checkNotNull(fundingOutputUserRefundAddress),
                ),
                checkNotNull(fundingOutputServerPaymentHashInHex),
                checkNotNull(fundingOutputServerPublicKeyInHex),
                checkNotNull(fundingOutputScriptVersion).toInt(),
                fundingOutputExpirationInBlocks?.toInt(),
                userPublicKey,
                muunPublicKey
            )

            val fees = if (swapSweepFeeInSatoshis != null) {
                SubmarineSwapFees(
                    checkNotNull(swapLightningFeeInSatoshis),
                    swapSweepFeeInSatoshis
                )
            } else {
                null
            }

            swap = SubmarineSwap(
                swapId,
                checkNotNull(swapUuid),
                checkNotNull(swapInvoice),
                receiver,
                fundingOutput,
                fees,
                checkNotNull(swapExpiresAt),
                swapPayedAt,
                swapPreimageInHex,
                null,
                null
            )
        } else {
            swap = null
        }

        val incomingSwap = if (incomingSwapId != null) {
            val htlc = if (htlcId != null) {
                IncomingSwapHtlc(
                    htlcId,
                    checkNotNull(htlcUuid),
                    checkNotNull(htlcExpirationHeight),
                    checkNotNull(htlcFulfillmentFeeSubsidyInSatoshis),
                    checkNotNull(htlcLentInSatoshis),
                    checkNotNull(htlcSwapServerPublicKeyInHex).let(Encodings::hexToBytes),
                    htlcFulfillmentTxInHex?.let(Encodings::hexToBytes),
                    checkNotNull(htlcAddress),
                    checkNotNull(htlcOutputAmountInSatoshis),
                    checkNotNull(htlcTxInHex).let(Encodings::hexToBytes)
                )
            } else {
                null
            }

            IncomingSwap(
                incomingSwapId,
                checkNotNull(incomingSwapUuid),
                checkNotNull(incomingSwapPaymentHashInHex).let(Encodings::hexToBytes),
                htlc,
                incomingSwapSphinxPacketInHex?.let(Encodings::hexToBytes),
                checkNotNull(incomingSwapCollectInSatoshis),
                checkNotNull(incomingSwapPaymentAmountInSatoshis),
                incomingSwapPreimageInHex?.let(Encodings::hexToBytes)
            )
        } else {
            null
        }

        return Operation(
            id,
            hid,
            direction,
            isExternal,
            senderProfile,
            senderIsExternal,
            receiverProfile,
            receiverIsExternal,
            receiverAddress,
            receiverAddressDerivationPath,
            BitcoinAmount(amountInSatoshis, amountInInputCurrency, amountInPrimaryCurrency),
            BitcoinAmount(feeInSatoshis, feeInInputCurrency, feeInPrimaryCurrency),
            confirmations,
            hash,
            description,
            metadata,
            status,
            creationDate,
            exchangeRateWindowHid,
            swap,
            incomingSwap,
            isRbf
        )
    }
}