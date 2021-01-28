package io.muun.apollo.domain.action.incoming_swap

import io.muun.apollo.data.preferences.ForwardingPoliciesRepository
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.libwallet.Invoice
import io.muun.apollo.domain.libwallet.errors.NoInvoicesLeftError
import io.muun.apollo.domain.utils.onTypedErrorResumeNext
import org.bitcoinj.core.NetworkParameters
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateInvoiceAction @Inject constructor(
        private val registerInvoices: RegisterInvoicesAction,
        private val forwardingPoliciesRepository: ForwardingPoliciesRepository,
        private val keysRepository: KeysRepository,
        private val networkParameters: NetworkParameters
): BaseAsyncAction0<String>() {

    override fun action(): Observable<String> {
        return generateInvoice()
            .onTypedErrorResumeNext(NoInvoicesLeftError::class.java) {
                registerInvoices.action()
                    .flatMap { generateInvoice() }
            }
            .doOnCompleted(registerInvoices::run)
    }

    private fun generateInvoice() = keysRepository.basePrivateKey
        .flatMap { basePrivateKey ->
            Observable.just(
                Invoice.generate(
                    networkParameters,
                    basePrivateKey,
                    forwardingPoliciesRepository.fetchOne().random()
                )
            )
        }

}