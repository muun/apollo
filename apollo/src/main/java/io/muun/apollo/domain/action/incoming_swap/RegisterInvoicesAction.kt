package io.muun.apollo.domain.action.incoming_swap

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.libwallet.Invoice
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegisterInvoicesAction @Inject constructor(
        private val keysRepository: KeysRepository,
        private val houstonClient: HoustonClient
): BaseAsyncAction0<Void>() {

    override fun action(): Observable<Void> {
        return Observable.defer {
            val invoices = Invoice.generateSecrets(keysRepository.basePublicKeyPair)

            houstonClient.registerInvoices(invoices.list())
                    .andThen(Observable.fromCallable {
                        Invoice.persistSecrets(invoices)
                        null
                    })
        }
    }

}