package io.muun.apollo.presentation.ui.setup_pin_code;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.view.MuunPinInput;
import io.muun.common.exception.MissingCaseError;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import butterknife.BindString;
import butterknife.BindView;


public class SetUpPinCodeActivity extends BaseActivity<SetUpPinCodePresenter>
        implements SetUpPinCodeView {

    public static Intent getIntent(Context context, boolean canCancel) {
        return new Intent(context, SetUpPinCodeActivity.class)
                .putExtra(CAN_CANCEL, canCancel);
    }

    private static final int AFTER_SUCCESS_DELAY_MS = 200;

    @BindView(R.id.set_up_pin_input)
    MuunPinInput pinInput;

    @BindView(R.id.back_arrow)
    View backButton;

    @BindString(R.string.pin_error_on_setup_cancel)
    String pinErrorOnCancel;

    @Override
    protected int getLayoutResource() {
        return R.layout.set_up_pin_code;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();
        pinInput.setListener(presenter::submitPin);
        backButton.setOnClickListener((v) -> presenter.goBack());
    }

    /**
     * Update the UI to reflect the current Stage of the process.
     */
    @Override
    public void setStep(SetUpPinCodeStep step) {

        switch (step) {
            case CHOOSE_PIN:
                pinInput.clear();
                onChoosePin();
                break;

            case REPEAT_PIN:
                onRepeatPin();
                break;

            default:
                throw new MissingCaseError(step);
        }
    }

    private void onChoosePin() {
        pinInput.clear();
        backButton.setVisibility(View.GONE);
        pinInput.setTitle(R.string.choose_your_pin);
        pinInput.setDescription(R.string.choose_your_pin_description);
    }

    private void onRepeatPin() {
        new Handler().postDelayed(() -> pinInput.clear(), AFTER_SUCCESS_DELAY_MS);
        backButton.setVisibility(View.VISIBLE);
        pinInput.setTitle(R.string.confirm_your_pin);
        pinInput.setDescription(R.string.choose_your_pin_repeat_description);
    }

    @Override
    public void clearPin() {
        pinInput.clear();
    }

    @Override
    public void reportPinError() {
        pinInput.flashError(() ->
                pinInput.setErrorMessage(R.string.your_pin_did_not_match)
        );
    }

    @Override
    public void reportPinSuccess() {
        pinInput.setSuccess();
        new Handler().postDelayed(this::finishOk, AFTER_SUCCESS_DELAY_MS);
    }

    private void finishOk() {
        setResult(RESULT_OK);
        finishActivity();
    }

    @Override
    public void onBackPressed() {

        if (backButton.getVisibility() == View.VISIBLE) {
            // As long as a back arrow is visible,
            // the backPressed navigation should behave in the same way as that arrow
            presenter.goBack();
        } else if (getArgumentsBundle().getBoolean(CAN_CANCEL, true)) {
            super.onBackPressed();
        } else {
            showTextToast(pinErrorOnCancel);
        }
    }
}
