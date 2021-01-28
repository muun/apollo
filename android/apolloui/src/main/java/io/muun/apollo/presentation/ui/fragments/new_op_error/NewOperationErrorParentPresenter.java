package io.muun.apollo.presentation.ui.fragments.new_op_error;

import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.ParentPresenter;
import io.muun.common.Optional;

public interface NewOperationErrorParentPresenter extends ParentPresenter {

    void goHomeInDefeat();

    Optional<PaymentRequest> getPaymentRequest();

}
