package io.muun.apollo.domain.selector

import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.libwallet.Invoice
import io.muun.apollo.domain.model.Operation
import rx.Observable
import rx.functions.Func1
import javax.inject.Inject

class WaitForIncomingLnPaymentSelector @Inject constructor(
    private val latestOperationSel: LatestOperationSelector
) {

    fun watchInvoice(invoice: String): Observable<Operation> =
        watch(Invoice.parseInvoice(Globals.INSTANCE.network, invoice).paymentHash)

    fun watch(paymentHash: ByteArray): Observable<Operation> {
        return latestOperationSel.watch().filter { maybeOp ->
            if (maybeOp.isPresent && maybeOp.get().isIncomingSwap) {
                if (maybeOp.get().incomingSwap!!.paymentHash.contentEquals(paymentHash)) {
                    return@filter true
                }
            }

            return@filter false
        }.map { maybeOp -> maybeOp.get() }
    }

    fun watch(predicate: Func1<ByteArray, Boolean>): Observable<Operation> {
        return latestOperationSel.watch()
            .filter { maybeOp ->
                if (maybeOp.isPresent && maybeOp.get().isIncomingSwap) {
                    return@filter predicate.call(maybeOp.get().incomingSwap!!.paymentHash)
                }

                return@filter false
            }
            .map { maybeOp -> maybeOp.get() }
    }

    fun get(paymentHash: ByteArray): Operation {
        return watch(paymentHash).toBlocking().first()
    }
}