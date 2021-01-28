package io.muun.apollo.domain.action.operation

import io.muun.apollo.domain.action.base.BaseAsyncAction1
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
    private val resolveLnUri: ResolveLnUriAction

): BaseAsyncAction1<OperationUri, PaymentRequest>() {

    override fun action(uri: OperationUri): Observable<PaymentRequest> {
        return Observable.defer {
            when {
                // First, check if this is an internal Muun URI (contact or hardware wallet):
                uri.isMuun -> resolveMuunUri.action(uri)

                // Second, if the URI has a LN invoice, prioritize it:
                uri.lnInvoice.isPresent -> resolveLnUri.action(uri)

                // Third, try looking for a Bitcoin address (BIP21 or BIP72):
                uri.bitcoinAddress.isPresent || uri.asyncUrl.isPresent ->
                    resolveBitcoinUri.action(uri)

                // No? Damn son.
                else -> throw MissingCaseError(uri.toString(), "Operation URI resolution")
            }
        }
    }
}
