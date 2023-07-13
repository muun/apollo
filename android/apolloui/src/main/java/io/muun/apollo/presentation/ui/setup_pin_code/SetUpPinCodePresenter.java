package io.muun.apollo.presentation.ui.setup_pin_code;

import io.muun.apollo.data.os.authentication.PinManager;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.exception.MissingCaseError;

import android.os.Bundle;
import icepick.State;

import javax.inject.Inject;


@PerActivity
public class SetUpPinCodePresenter extends BasePresenter<SetUpPinCodeView> {

    private final PinManager pinManager;
    private final ApplicationLockManager lockManager;

    @State
    SetUpPinCodeStep step = SetUpPinCodeStep.CHOOSE_PIN;

    @State
    String chosenPin = null;

    @Inject
    public SetUpPinCodePresenter(PinManager pinManager, ApplicationLockManager lockManager) {
        this.pinManager = pinManager;
        this.lockManager = lockManager;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);
        setStep(step);
    }

    /**
     * Call when the user has entered a PIN.
     */
    public void submitPin(String pin) {
        switch (step) {
            case CHOOSE_PIN:
                onPinChosen(pin);
                break;

            case REPEAT_PIN:
                onPinRepeated(pin);
                break;

            default:
                throw new MissingCaseError(step);
        }
    }

    private void onPinChosen(String pin) {
        chosenPin = pin;
        setStep(SetUpPinCodeStep.REPEAT_PIN);
    }

    private void onPinRepeated(String pin) {
        if (pin.equals(chosenPin)) {
            analytics.report(new AnalyticsEvent.E_PIN(AnalyticsEvent.PIN_TYPE.CREATED));
            pinManager.storePin(pin);
            lockManager.tryUnlockWithPin(pin); // will succeed
            view.reportPinSuccess();

        } else {
            analytics.report(new AnalyticsEvent.E_PIN(AnalyticsEvent.PIN_TYPE.DID_NOT_MATCH));
            view.reportPinError();
        }
    }

    private void setStep(SetUpPinCodeStep step) {
        this.step = step;

        if (step == SetUpPinCodeStep.CHOOSE_PIN) {
            analytics.report(new AnalyticsEvent.S_PIN_CHOOSE());
        } else {
            analytics.report(new AnalyticsEvent.S_PIN_REPEAT());
        }

        view.setStep(step);
    }

    /**
     * Call when the user clicks the back navigation button.
     */
    public void goBack() {
        if (step == SetUpPinCodeStep.REPEAT_PIN) {
            setStep(SetUpPinCodeStep.CHOOSE_PIN);
        }
    }
}
