package io.muun.apollo.domain.selector

import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.libwallet.Invoice
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.model.Sha256Hash
import rx.Observable
import rx.functions.Func1
import javax.inject.Inject

class WaitForIncomingLnPaymentSelector @Inject constructor(
    private val latestOperationSel: LatestOperationSelector
) {

    fun watchInvoice(invoice: String): Observable<Operation> =
        watch(Invoice.decodeInvoice(Globals.INSTANCE.network, invoice).paymentHash)

    fun watch(paymentHash: Sha256Hash): Observable<Operation> {
        return latestOperationSel.watch()
            .filter { maybeOp -> maybeOp.isPresent && maybeOp.get().isIncomingSwap }
            .filter { maybeOp -> maybeOp.get().incomingSwap!!.getPaymentHash() == paymentHash }
            .map { maybeOp -> maybeOp.get() }
    }

    fun watch(predicate: Func1<Sha256Hash, Boolean>): Observable<Operation> {
        return latestOperationSel.watch()
            .filter { maybeOp -> maybeOp.isPresent && maybeOp.get().isIncomingSwap }
            .filter { maybeOp -> predicate.call(maybeOp.get().incomingSwap!!.getPaymentHash()) }
            .map { maybeOp -> maybeOp.get() }
    }

    fun get(paymentHash: Sha256Hash): Operation {
        return watch(paymentHash).toBlocking().first()
    }
}