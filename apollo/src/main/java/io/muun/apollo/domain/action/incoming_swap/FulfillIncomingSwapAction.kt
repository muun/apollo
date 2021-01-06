package io.muun.apollo.domain.action.incoming_swap

import io.muun.apollo.data.db.incoming_swap.IncomingSwapDao
import io.muun.apollo.data.db.operation.OperationDao
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.libwallet.IncomingSwap
import io.muun.apollo.domain.model.Operation
import io.muun.common.api.RawTransaction
import io.muun.common.api.error.ErrorCode
import io.muun.common.exception.HttpException
import io.muun.common.rx.RxHelper
import io.muun.common.utils.Encodings
import libwallet.Libwallet
import org.bitcoinj.core.NetworkParameters
import rx.Completable
import rx.Observable
import rx.Single
import javax.inject.Inject

open class FulfillIncomingSwapAction
@Inject constructor(
        private val houstonClient: HoustonClient,
        private val operationDao: OperationDao,
        private val keysRepository: KeysRepository,
        private val network: NetworkParameters,
        private val incomingSwapDao: IncomingSwapDao,
) : BaseAsyncAction1<String, Unit>() {

    override fun action(incomingSwapUuid: String): Observable<Unit> {

        return Single.zip(
                operationDao.fetchByIncomingSwapUuid(incomingSwapUuid).first().toSingle(),
                houstonClient.fetchFulfillmentData(incomingSwapUuid),
                Operation::to
        )
                .map { (op, data) ->
                    op to IncomingSwap.signFulfillment(
                            op.incomingSwap!!,
                            data,
                            keysRepository.basePrivateKey.toBlocking().first(),
                            keysRepository.baseMuunPublicKey,
                            network
                    )
                }
                .map { (op, tx) -> op to RawTransaction(Encodings.bytesToHex(tx)) }
                .flatMap { (op, rawTx) ->
                    houstonClient.pushFulfillmentTransaction(incomingSwapUuid, rawTx)
                            .andThen(Single.just(op))
                }
                .map { op ->
                    checkNotNull(op.incomingSwap)
                    op.incomingSwap.preimage = Libwallet.exposePreimage(op.incomingSwap.paymentHash)
                    op
                }
                .flatMapCompletable { op ->
                    checkNotNull(op.incomingSwap)
                    Observable.zip(
                            incomingSwapDao.store(op.incomingSwap),
                            // We need to store the operation so that it will trigger a refresh
                            // of the operation list / detail
                            operationDao.store(op),
                            RxHelper::toVoid
                    ).toCompletable()
                }
                .onErrorResumeNext { e ->
                    if (e is HttpException
                            && e.errorCode == ErrorCode.INCOMING_SWAP_ALREADY_FULFILLED) {
                        Completable.complete()
                    } else {
                        Completable.error(e)
                    }
                }
                .toObservable()

    }

}