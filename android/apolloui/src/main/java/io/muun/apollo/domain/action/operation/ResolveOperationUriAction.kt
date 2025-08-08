package io.muun.apollo.domain.action.operation

import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.analytics.NewOperationOrigin
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.common.exception.MissingCaseError
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolve an OperationUri, by fetching all necessary information from local and remote sources.
 */
@Singleton
class ResolveOperationUriAction @Inject constructor(
    private val resolveBitcoinUri: ResolveBitcoinUriAction,
    private val resolveMuunUri: ResolveMuunUriAction,
    private val resolveLnInvoice: ResolveLnInvoiceAction,
) : BaseAsyncAction2<OperationUri, NewOperationOrigin, PaymentRequest>() {

    override fun action(uri: OperationUri, origin: NewOperationOrigin): Observable<PaymentRequest> {
        return Observable.defer {
            when {
                // First, check if this is an internal Muun URI (contact or hardware wallet):
                uri.isMuun -> resolveMuunUri.action(uri)

                // Second, if the URI has a LN invoice, prioritize it:
                uri.lnInvoice.isPresent -> resolveLnInvoice.action(uri.lnInvoice.get(), origin)

                // Third, try looking for a Bitcoin address (BIP21 or BIP72):
                uri.bitcoinAddress.isPresent || uri.asyncUrl.isPresent ->
                    resolveBitcoinUri.action(uri)

                // No? Damn son.
                else -> throw MissingCaseError(uri.toString(), "Operation URI resolution")
            }
        }
    }
}
