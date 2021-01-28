package io.muun.apollo.presentation.ui.fragments.new_op_error;

import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.PaymentContext;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.new_operation.NewOperationErrorType;
import io.muun.common.Optional;

public interface NewOperationErrorView extends BaseView {

    String ARG_ERROR_TYPE = "error_type";

    void setErrorType(NewOperationErrorType errorType);

    void setCurrencyDisplayMode(CurrencyDisplayMode mode);

    void setPaymentContextForError(
            PaymentContext payCtx,
            Optional<PaymentRequest> payReq,
            NewOperationErrorType errorType
    );

}
