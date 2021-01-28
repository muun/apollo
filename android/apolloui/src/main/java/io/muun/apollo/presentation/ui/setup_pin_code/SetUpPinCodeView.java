package io.muun.apollo.presentation.ui.setup_pin_code;

import io.muun.apollo.presentation.ui.base.BaseView;

public interface SetUpPinCodeView extends BaseView {

    String CAN_CANCEL = "can_cancel";

    void setStep(SetUpPinCodeStep stage);

    void clearPin();

    void reportPinError();

    void reportPinSuccess();
}
