package io.muun.apollo.domain.action.operation;

import io.muun.apollo.domain.action.base.BaseAsyncAction2;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.common.Optional;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SubmitPaymentAction extends BaseAsyncAction2<
        PaymentRequest,
        PreparedPayment,
        Optional<Operation>> {

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
    public Observable<Optional<Operation>> action(PaymentRequest payReq, PreparedPayment payment) {
        return Observable.defer(() -> submitPayment(payReq, payment));
    }

    private Observable<Optional<Operation>> submitPayment(PaymentRequest payReq,
                                                          PreparedPayment prepPayment) {

        if (payReq.getType() == PaymentRequest.Type.FROM_HARDWARE_WALLET) {
            return submitIncomingPayment.action(payReq, prepPayment)
                    .map(aVoid -> Optional.empty());

        } else {
            return submitOutgoingPayment.action(payReq, prepPayment)
                    .map(Optional::of);
        }
    }
}
