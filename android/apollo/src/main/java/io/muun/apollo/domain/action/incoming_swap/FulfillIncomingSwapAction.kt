package io.muun.apollo.domain.action.incoming_swap

import io.muun.apollo.data.db.base.ElementNotFoundException
import io.muun.apollo.data.db.incoming_swap.IncomingSwapDao
import io.muun.apollo.data.db.operation.OperationDao
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.libwallet.errors.UnfulfillableIncomingSwapError
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.utils.isInstanceOrIsCausedByError
import io.muun.common.Optional
import io.muun.common.api.RawTransaction
import io.muun.common.api.error.ErrorCode
import io.muun.common.exception.HttpException
import io.muun.common.rx.RxHelper
import io.muun.common.utils.Encodings
import org.bitcoinj.core.NetworkParameters
import rx.Completable
import rx.Observable
import rx.Single
import timber.log.Timber
import javax.inject.Inject

open class FulfillIncomingSwapAction @Inject constructor(
        private val houstonClient: HoustonClient,
        private val operationDao: OperationDao,
        private val keysRepository: KeysRepository,
        private val network: NetworkParameters,
        private val incomingSwapDao: IncomingSwapDao,
) : BaseAsyncAction1<String, Unit>() {

    override fun action(incomingSwapUuid: String): Observable<Unit> {

        return operationDao.fetchByIncomingSwapUuid(incomingSwapUuid).first().toSingle()
                .flatMapCompletable(this::fulfill)
                .onErrorResumeNext { t ->
                    if (t.isInstanceOrIsCausedByError<ElementNotFoundException>()) {
                        return@onErrorResumeNext operationDao.fetchMaybeLatest()
                                .flatMap{ maybeOp  ->
                                    Timber.e(
                                            "Failed fulfilled due to missing. Latest op id = %d",
                                            maybeOp.map(Operation::hid).orElse(-1)
                                    )

                                    Observable.error<Optional<Operation>>(t)
                                }
                                .toCompletable()
                    }

                    Completable.error(t)
                }
                .toObservable()
    }

    private fun fulfill(op: Operation): Completable {

        checkNotNull(op.incomingSwap)

        val fulfill = if (op.incomingSwap.htlc != null) {
            fulfillOnChain(op)
        } else {
            fulfillFullDebt(op)
        }

        return verify(op)
            .andThen(fulfill)
            .andThen(persistPreimage(op))
            .onErrorResumeNext { e ->
                if (e is UnfulfillableIncomingSwapError) {
                    houstonClient.expireInvoice(op.incomingSwap.getPaymentHash())
                } else {
                    Completable.error(e)
                }
            }
            .onErrorComplete { e ->
                e is HttpException && e.errorCode == ErrorCode.INCOMING_SWAP_ALREADY_FULFILLED
            }
    }

    private fun fulfillFullDebt(op: Operation): Completable {
        checkNotNull(op.incomingSwap)

        return Completable.defer {
            val preimage = op.incomingSwap.fulfillFullDebt().preimage
            houstonClient.fulfillIncomingSwap(op.incomingSwap.houstonUuid, preimage.toBytes())
        }
    }

    private fun fulfillOnChain(op: Operation): Completable {
        checkNotNull(op.incomingSwap)
        checkNotNull(op.incomingSwap.htlc)

        return houstonClient.fetchFulfillmentData(op.incomingSwap.houstonUuid)
            .flatMap { data ->
                Single.fromCallable {
                    op.incomingSwap.fulfill(
                        data,
                        fetchUserPrivateKey(),
                        keysRepository.baseMuunPublicKey,
                        network
                    )
                }
            }
            .map { RawTransaction(Encodings.bytesToHex(it.fullfillmentTx!!)) }
            .flatMapCompletable { tx ->
                houstonClient.pushFulfillmentTransaction(op.incomingSwap.houstonUuid, tx)
            }
    }

    private fun fetchUserPrivateKey() = keysRepository.basePrivateKey.toBlocking().first()

    private fun persistPreimage(op: Operation) = Completable.defer {
        checkNotNull(op.incomingSwap)

        Observable.zip(
            incomingSwapDao.store(op.incomingSwap),
            // We need to store the operation so that it will trigger a refresh
            // of the operation list / detail
            operationDao.store(op),
            RxHelper::toVoid
        ).toCompletable()
    }

    private fun verify(op: Operation) = Completable.fromAction {
        checkNotNull(op.incomingSwap)
        op.incomingSwap.verifyFulfillable(fetchUserPrivateKey(), network)
    }

}