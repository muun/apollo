package io.muun.apollo.presentation.ui.fragments.manual_fee;

import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.ParentPresenter;

public interface ManualFeeParentPresenter extends ParentPresenter {

    PaymentRequest getPaymentRequest();

    void confirmFee(double selectedFeeOption);
}

