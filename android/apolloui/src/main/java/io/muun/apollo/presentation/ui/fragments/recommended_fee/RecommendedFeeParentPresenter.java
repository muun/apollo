package io.muun.apollo.presentation.ui.fragments.recommended_fee;

import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.ParentPresenter;

public interface RecommendedFeeParentPresenter extends ParentPresenter {

    PaymentRequest getPaymentRequest();

    void confirmFee(double selectedFeeOption);

    void editFeeManually();
}
