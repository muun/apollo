package io.muun.apollo.presentation.ui.setup_pin_code;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SetUpPinCodeBinding;
import io.muun.apollo.presentation.biometrics.BiometricsController;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.common.exception.MissingCaseError;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import androidx.viewbinding.ViewBinding;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import javax.inject.Inject;


public class SetUpPinCodeActivity extends BaseActivity<SetUpPinCodePresenter>
        implements SetUpPinCodeView {

    public static Intent getIntent(Context context, boolean canCancel) {
        return new Intent(context, SetUpPinCodeActivity.class)
                .putExtra(CAN_CANCEL, canCancel);
    }

    @Inject
    BiometricsController biometricsController;

    private static final int AFTER_SUCCESS_DELAY_MS = 200;
    private static final int PIN_LENGTH = 6;

    private SetUpPinCodeBinding binding() {
        return (SetUpPinCodeBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return SetUpPinCodeBinding::inflate;
    }

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
        final var binding = binding();
        binding.setUpPinInput.setPinLength(PIN_LENGTH);
        binding.setUpPinInput.setListener(presenter::submitPin);
        binding.backArrow.setOnClickListener((v) -> presenter.goBack());
    }

    /**
     * Update the UI to reflect the current Stage of the process.
     */
    @Override
    public void setStep(SetUpPinCodeStep step) {
        final var binding = binding();
        final var pinInput = binding.setUpPinInput;

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
        final var binding = binding();
        final var pinInput = binding.setUpPinInput;
        final var backButton = binding.backArrow;

        pinInput.clear();
        backButton.setVisibility(View.GONE);
        pinInput.setTitle(R.string.choose_your_pin);
        pinInput.setDescription(R.string.choose_your_pin_description);
    }

    private void onRepeatPin() {
        final var binding = binding();
        final var pinInput = binding.setUpPinInput;
        final var backButton = binding.backArrow;

        new Handler().postDelayed(() -> pinInput.clear(), AFTER_SUCCESS_DELAY_MS);
        backButton.setVisibility(View.VISIBLE);
        pinInput.setTitle(R.string.confirm_your_pin);
        pinInput.setDescription(R.string.choose_your_pin_repeat_description);
    }

    @Override
    public void clearPin() {
        binding().setUpPinInput.clear();
    }

    @Override
    public void reportPinError() {
        final var pinInput = binding().setUpPinInput;

        pinInput.flashError(() ->
                pinInput.setErrorMessage(R.string.your_pin_did_not_match)
        );
    }

    @Override
    public void reportPinSuccess() {
        binding().setUpPinInput.setSuccess();
        new Handler().postDelayed(
                this::setupBiometricsIfPossibleAndFinishOk,
                AFTER_SUCCESS_DELAY_MS
        );
    }

    private void setupBiometricsIfPossibleAndFinishOk() {
        if (biometricsController.getAuthenticationStatus().getCanAuthenticate()) {
            biometricsController.authenticate(this,
                    getString(R.string.biometrics_setup_title),
                    getString(R.string.biometrics_setup_subtitle),
                    () -> {
                        biometricsController.setUserOptInBiometrics(true);
                        finishOk();
                        return Unit.INSTANCE;
                    },
                    (error) -> {
                        finishOk();
                        return Unit.INSTANCE;
                    }
            );
        } else {
            finishOk();
        }
    }

    private void finishOk() {
        setResult(RESULT_OK);
        finishActivity();
    }

    @Override
    public void onBackPressed() {
        if (binding().backArrow.getVisibility() == View.VISIBLE) {
            // As long as a back arrow is visible,
            // the backPressed navigation should behave in the same way as that arrow
            presenter.goBack();
        } else if (getArgumentsBundle().getBoolean(CAN_CANCEL, true)) {
            super.onBackPressed();
        } else {
            showTextToast(getResources().getString(R.string.pin_error_on_setup_cancel));
        }
    }
}
