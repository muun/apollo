package io.muun.apollo.domain.action.incoming_swap

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.libwallet.errors.UnfulfillableIncomingSwapError
import io.muun.apollo.domain.model.IncomingSwap
import org.bitcoinj.core.NetworkParameters
import rx.Completable
import timber.log.Timber
import javax.inject.Inject

class VerifyFulfillableAction
@Inject constructor(
    private val keysRepository: KeysRepository,
    private val houstonClient: HoustonClient,
    private val networkParameters: NetworkParameters,
) {
    fun action(swap: IncomingSwap): Completable {

        return keysRepository.basePrivateKey
            .flatMapCompletable { userKey ->

                try {
                    swap.verifyFulfillable(userKey, networkParameters)
                } catch (e: UnfulfillableIncomingSwapError) {
                    Timber.e("Will expire invoice due to unfulfillable swap", e)

                    houstonClient.expireInvoice(swap.paymentHash)
                }

                Completable.complete()

            }.toCompletable()
    }
}