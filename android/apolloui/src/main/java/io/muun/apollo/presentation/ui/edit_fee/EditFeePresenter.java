package io.muun.apollo.presentation.ui.edit_fee;

import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.fragments.manual_fee.ManualFeeFragment;
import io.muun.apollo.presentation.ui.fragments.manual_fee.ManualFeeParentPresenter;
import io.muun.apollo.presentation.ui.fragments.recommended_fee.RecommendedFeeParentPresenter;

import android.app.Activity;
import android.os.Bundle;

import javax.inject.Inject;

@PerActivity
public class EditFeePresenter extends BasePresenter<EditFeeView> implements
        RecommendedFeeParentPresenter, ManualFeeParentPresenter {

    private PaymentRequest payReq;

    /**
     * Creates a presenter.
     */
    @Inject
    public EditFeePresenter() {
    }

    @Override
    public void setUp(Bundle arguments) {
        super.setUp(arguments);

        this.payReq = EditFeeActivity.getPaymentRequest(arguments);
    }

    @Override
    public PaymentRequest getPaymentRequest() {
        return payReq;
    }

    @Override
    public void confirmFee(double selectedFeeRate) {
        view.finishWithResult(Activity.RESULT_OK, selectedFeeRate);
    }

    @Override
    public void editFeeManually() {
        view.replaceFragment(new ManualFeeFragment(), true);
    }
}
