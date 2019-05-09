package io.muun.apollo.domain.action.operation;

import io.muun.apollo.domain.action.base.BaseAsyncAction2;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PreparedPayment;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SubmitPaymentAction extends BaseAsyncAction2<
        PaymentRequest,
        PreparedPayment,
        Void> {

    private final SubmitIncomingPaymentAction submitIncomingPayment;
    private final SubmitOutgoingPaymentAction submitOutgoingPayment;

    /**
     * Submit a payment to Houston, and update local data in response.
     */
    @Inject
    public SubmitPaymentAction(SubmitIncomingPaymentAction submitIncomingPayment,
                               SubmitOutgoingPaymentAction submitOutgoingPayment) {

        this.submitIncomingPayment = submitIncomingPayment;
        this.submitOutgoingPayment = submitOutgoingPayment;
    }

    @Override
    public Observable<Void> action(PaymentRequest payReq, PreparedPayment prepPayment) {
        return Observable.defer(() -> submitPayment(payReq, prepPayment));
    }

    private Observable<Void> submitPayment(PaymentRequest payReq, PreparedPayment prepPayment) {
        if (payReq.type == PaymentRequest.Type.FROM_HARDWARE_WALLET) {
            return submitIncomingPayment.action(payReq, prepPayment);
        } else {
            return submitOutgoingPayment.action(payReq, prepPayment);
        }
    }
}
