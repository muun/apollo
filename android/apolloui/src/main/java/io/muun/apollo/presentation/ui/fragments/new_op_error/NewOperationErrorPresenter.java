package io.muun.apollo.presentation.ui.fragments.new_op_error;

import io.muun.apollo.domain.model.PaymentContext;
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.apollo.presentation.ui.new_operation.NewOperationErrorType;
import io.muun.common.utils.Preconditions;

import android.os.Bundle;
import rx.Observable;

import javax.inject.Inject;

import static io.muun.apollo.presentation.ui.fragments.new_op_error.NewOperationErrorView.ARG_ERROR_TYPE;

@PerFragment
public class NewOperationErrorPresenter extends
        SingleFragmentPresenter<NewOperationErrorView, NewOperationErrorParentPresenter> {

    private final CurrencyDisplayModeSelector currencyDisplayModeSel;

    @Inject
    public NewOperationErrorPresenter(CurrencyDisplayModeSelector currencyDisplayModeSel) {
        this.currencyDisplayModeSel = currencyDisplayModeSel;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        final NewOperationErrorType errorType = getErrorType(arguments);

        view.setErrorType(errorType);

        // Not all new op errors need/make use of PaymentContext and/or PaymentRequest. In fact,
        // an error could occur PRIOR to any of those being fully available. So, we need do some
        // filtering... For now, we're going with whitelisting the errors we know need these two.
        if (errorType == NewOperationErrorType.INSUFFICIENT_FUNDS) {
            view.setCurrencyDisplayMode(currencyDisplayModeSel.get());

            final Observable<PaymentContext> observable = getParentPresenter()
                    .watchPaymentContext()
                    .doOnNext(payCtx ->
                            view.setPaymentContextForError(
                                    payCtx,
                                    getParentPresenter().getPaymentRequest(),
                                    errorType
                            )
                    );

            subscribeTo(observable);
        }
    }

    public void goHomeInDefeat() {
        getParentPresenter().goHomeInDefeat();
    }

    public void contactSupport() {
        navigator.navigateToSendErrorFeedback(getContext());
    }

    private NewOperationErrorType getErrorType(Bundle arguments) {
        final String errorTypeName = arguments.getString(ARG_ERROR_TYPE);

        Preconditions.checkState(errorTypeName != null);

        return NewOperationErrorType.valueOf(errorTypeName);
    }
}

