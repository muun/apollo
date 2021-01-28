package io.muun.apollo.presentation.ui.new_operation;

import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.PaymentAnalysis;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.BaseView;

public interface NewOperationView extends BaseView {

    void setCurrencyDisplayMode(CurrencyDisplayMode mode);

    void setForm(NewOperationForm form);

    void setPaymentAnalysis(PaymentAnalysis analysis);

    void setLoading(boolean isLoading);

    void setConnectedToNetwork(boolean isConnected);

    void editFee(PaymentRequest paymentRequest);

    void showErrorScreen(NewOperationErrorType type);
}
