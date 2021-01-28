package io.muun.apollo.presentation.ui.fragments.manual_fee;

import io.muun.apollo.domain.model.WithPaymentContext;
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.analytics.AnalyticsEvent.S_MORE_INFO_TYPE;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.di.PerFragment;

import android.os.Bundle;
import androidx.annotation.Nullable;

import javax.inject.Inject;

@PerFragment
public class ManualFeePresenter
        extends SingleFragmentPresenter<ManualFeeView, ManualFeeParentPresenter>
        implements WithPaymentContext {

    private final CurrencyDisplayModeSelector currencyDisplayModeSel;

    /**
     * Creates a presenter.
     */
    @Inject
    public ManualFeePresenter(CurrencyDisplayModeSelector currencyDisplayModeSel) {
        this.currencyDisplayModeSel = currencyDisplayModeSel;
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        view.setCurrencyDisplayMode(currencyDisplayModeSel.get());

        // TODO using a blocking observable, good place to start looking for performance problems
        view.setPaymentContext(
                getPaymentContext(),
                getParentPresenter().getPaymentRequest()
        );
    }

    public void confirmFee(double selectedFeeRate) {
        getParentPresenter().confirmFee(selectedFeeRate);
    }

    @Nullable
    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_MANUALLY_ENTER_FEE();
    }

    public void reportShowManualFeeInfo() {
        analytics.report(new AnalyticsEvent.S_MORE_INFO(S_MORE_INFO_TYPE.MANUAL_FEE));
    }
}
