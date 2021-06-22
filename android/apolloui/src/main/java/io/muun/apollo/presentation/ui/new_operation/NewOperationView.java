package io.muun.apollo.presentation.ui.new_operation;

import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.PaymentAnalysis;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.BaseView;

import androidx.annotation.NonNull;

public interface NewOperationView extends BaseView {

    void setCurrencyDisplayMode(CurrencyDisplayMode mode);

    void setForm(NewOperationForm form);

    void setPaymentAnalysis(PaymentAnalysis analysis);

    void setLoading(boolean isLoading);

    void setConnectedToNetwork(boolean isConnected);

    void editFee(@NonNull PaymentRequest paymentRequest);

    void editFeeManually(@NonNull PaymentRequest paymentRequest);

    void confirmFee(double selectedFeeRate);

    void showErrorScreen(NewOperationErrorType type);

}
