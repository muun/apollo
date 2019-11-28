package io.muun.apollo.domain.action.operation;

import io.muun.apollo.domain.action.AddressActions;
import io.muun.apollo.domain.action.SatelliteActions;
import io.muun.apollo.domain.action.base.BaseAsyncAction2;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PendingWithdrawal;
import io.muun.apollo.domain.model.PreparedPayment;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.utils.Preconditions;
import io.muun.common.utils.RandomGenerator;

import org.threeten.bp.ZonedDateTime;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SubmitIncomingPaymentAction extends BaseAsyncAction2<
        PaymentRequest,
        PreparedPayment,
        Void> {

    // TODO: remove these dependencies when related actions are extracted
    private final SatelliteActions satelliteActions;
    private final AddressActions addressActions;

    /**
     * Submit an incoming payment to Houston, and update local data in response.
     */
    @Inject
    public SubmitIncomingPaymentAction(SatelliteActions satelliteActions,
                                       AddressActions addressActions) {

        this.satelliteActions = satelliteActions;
        this.addressActions = addressActions;
    }

    @Override
    public Observable<Void> action(PaymentRequest payReq, PreparedPayment prepPayment) {
        return Observable.defer(() -> submitPayment(payReq, prepPayment));
    }

    private Observable<Void> submitPayment(PaymentRequest payReq, PreparedPayment prepPayment) {
        final MuunAddress address = addressActions.createLegacyAddress();

        Preconditions.checkNotNull(payReq.getHardwareWallet());

        final PendingWithdrawal pendingWithdrawal = new PendingWithdrawal(
                RandomGenerator.getRandomUuid(),
                payReq.getHardwareWallet().getHid(),
                address.getAddress(),
                address.getDerivationPath(),
                prepPayment.amount,
                prepPayment.fee,
                prepPayment.rateWindowHid,
                prepPayment.description,
                ZonedDateTime.now(),
                null
        );

        return satelliteActions.beginWithdrawal(pendingWithdrawal);
    }
}
