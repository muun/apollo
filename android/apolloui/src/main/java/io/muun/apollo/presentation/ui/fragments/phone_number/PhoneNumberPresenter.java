package io.muun.apollo.presentation.ui.fragments.phone_number;

import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.analytics.AnalyticsEvent.S_MORE_INFO_TYPE;
import io.muun.apollo.domain.errors.p2p.CountryNotSupportedError;
import io.muun.apollo.domain.errors.p2p.InvalidPhoneNumberError;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;
import io.muun.common.model.PhoneNumber;

import android.os.Bundle;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerFragment
public class PhoneNumberPresenter
        extends SingleFragmentPresenter<PhoneNumberView, PhoneNumberParentPresenter> {

    /**
     * Create a PhoneNumberPresenter.
     */
    @Inject
    public PhoneNumberPresenter() {
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {

        super.setUp(arguments);

        setUpSubmitPhoneNumber();
    }

    private void setUpSubmitPhoneNumber() {

        final Observable<?> observable = getParentPresenter()
                .watchSubmitPhoneNumber()
                .doOnNext(state -> {
                    view.setLoading(state.isLoading());

                    if (state.isError()) {
                        handleError(state.getError());
                    }
                });

        subscribeTo(observable);
    }

    /**
     * Receives the phone number after the user filled it.
     */
    public void submitPhoneNumber(String phoneNumberText) {
        final PhoneNumber phoneNumber;

        view.setPhoneNumberError(null);

        try {
            phoneNumber = new PhoneNumber(phoneNumberText);

        } catch (IllegalArgumentException error) {
            handleError(error);
            return;
        }

        getParentPresenter().submitPhoneNumber(phoneNumber);
    }

    @Override
    public void handleError(Throwable error) {
        // NOTE: these errors can be thrown both by Apollo and Houston

        if (error instanceof InvalidPhoneNumberError) {
            view.setPhoneNumberError((InvalidPhoneNumberError)error);

        } else if (error instanceof CountryNotSupportedError) {
            view.showTextToast(error.getMessage()); // Spinner doesn't support setError!

        } else {
            super.handleError(error);
        }
    }

    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_P2P_SETUP_PHONE();
    }

    /**
     * Called when phone number extra info is shown.
     */
    public void reportShowPhoneNumberInfo() {
        analytics.report(new AnalyticsEvent.S_MORE_INFO(S_MORE_INFO_TYPE.PHONE_NUMBER));
    }
}
