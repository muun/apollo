package io.muun.apollo.presentation.ui.edit_fee;

import io.muun.apollo.R;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.PaymentRequestJson;
import io.muun.apollo.presentation.ui.base.BaseFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.recommended_fee.RecommendedFeeFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.common.Optional;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import butterknife.BindView;

import javax.validation.constraints.NotNull;

public class EditFeeActivity extends SingleFragmentActivity<EditFeePresenter>
        implements EditFeeView {

    private static final String PAYMENT_REQUEST = "payment_request";
    private static final String SELECTED_FEE_RESULT = "selected_fee_result";

    @BindView(R.id.edit_fee_header)
    MuunHeader header;

    /**
     * Create an Intent to launch this Activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context, PaymentRequest payReq) {
        final String serialization = SerializationUtils.serializeJson(
                PaymentRequestJson.class,
                payReq.toJson()
        );

        return new Intent(context, EditFeeActivity.class)
                .putExtra(PAYMENT_REQUEST, serialization);
    }

    static PaymentRequest getPaymentRequest(@NotNull Bundle bundle) {
        final PaymentRequestJson payReqJson = SerializationUtils.deserializeJson(
                PaymentRequestJson.class,
                bundle.getString(PAYMENT_REQUEST)
        );

        return PaymentRequest.fromJson(payReqJson);
    }

    public static Optional<Double> getResult(Intent data) {
        return Optional.ifNonNegative(data.getDoubleExtra(SELECTED_FEE_RESULT, -1));
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.edit_fee_activity;
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.fragment_container;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.showTitle(R.string.edit_fee_title);
    }

    @Override
    protected BaseFragment getInitialFragment() {
        return new RecommendedFeeFragment();
    }

    @Override
    public MuunHeader getHeader() {
        return header;
    }

    @Override
    public void finishWithResult(int resultCode, double selectedFeeRate) {
        final Intent intent = new Intent();
        intent.putExtra(SELECTED_FEE_RESULT, selectedFeeRate);
        setResult(resultCode, intent);
        finishActivity();
    }
}
