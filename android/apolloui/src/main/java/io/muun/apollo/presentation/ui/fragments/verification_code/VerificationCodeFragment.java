package io.muun.apollo.presentation.ui.fragments.verification_code;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SignupVerificationFragmentBinding;
import io.muun.apollo.presentation.ui.MuunCountdownTimer;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.DrawerDialogFragment;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.VerificationType;
import io.muun.common.utils.Dates;

import android.content.Intent;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.viewbinding.ViewBinding;
import icepick.State;
import kotlin.jvm.functions.Function3;

public class VerificationCodeFragment extends SingleFragment<VerificationCodePresenter>
        implements VerificationCodeView, MuunCountdownTimer.CountDownTimerListener {

    private static final int DRAWER_REQUEST = 1;
    private static final int SEND_SMS_ACTION = 2;
    private static final int CALL_ME_ACTION = 3;

    private static final int RESEND_COUNTDOWN_MS = 90 * 1000;

    private SignupVerificationFragmentBinding binding() {
        return (SignupVerificationFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return SignupVerificationFragmentBinding::inflate;
    }

    @State
    long deadlineInRealtime = 0; // time when resendButton is re-enabled

    private CountDownTimer countdownTimer;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        binding().signupVerificationTextCode.setOnChangeListener(this, newText -> {
            final boolean enable = !TextUtils.isEmpty(newText) && newText.length() >= 6;
            binding().signupContinue.setEnabled(enable);
            binding().signupVerificationTextCode.setOnKeyboardNextListener(
                    enable ? binding().signupContinue::callOnClick : null
            );
        });

        binding().signupVerificationResend.setOnClickListener(v -> {
            final AppCompatDialogFragment drawer = new DrawerDialogFragment()
                    .addAction(
                            SEND_SMS_ACTION,
                            R.drawable.ic_gallery_24_px,
                            getString(R.string.action_resend_sms)
                    )
                    .addAction(
                            CALL_ME_ACTION,
                            R.drawable.ic_camera_24_px,
                            getString(R.string.action_call_me)
                    );

            requestExternalResult(DRAWER_REQUEST, drawer);
        });

        binding().signupContinue.setEnabled(false);
        binding().signupContinue.setOnClickListener(v -> {
            presenter.submitVerificationCode(
                    binding().signupVerificationTextCode.getText().toString()
            );
        });

        // Don't reset countdown if it's a fragment recreation (e.g. rotation change)
        if (deadlineInRealtime == 0) {
            resetCountdownDeadline();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        startCountdown();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding().signupVerificationTextCode.requestFocusInput();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopCountdown();
    }

    @Override
    public boolean onBackPressed() {
        finishActivity();
        return true;
    }

    @Override
    public void setLoading(boolean isLoading) {
        binding().signupVerificationTextCode.setEnabled(!isLoading);
        binding().signupContinue.setLoading(isLoading);
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {
        super.onExternalResult(requestCode, resultCode, data);

        if (requestCode == DRAWER_REQUEST) {
            onDrawerResult(resultCode);
        } else {
            throw new MissingCaseError(requestCode, "VerificationCodeFragment resultCode");
        }
    }

    private void handleResendVerification(VerificationType verificationType) {
        presenter.resendVerification(verificationType);

        resetCountdownDeadline();
        startCountdown();
    }

    @Override
    public void setPhoneNumber(String phoneNumber) {
        final CharSequence content = TextUtils.concat(
                getString(R.string.signup_verification_explanation),
                " to ",
                new RichText(UiUtils.convertToNonBreakableSpaces(phoneNumber)).setBold(),
                ". ",
                new RichText(getString(R.string.action_change_num))
                        .setLink(this::onChangePhoneNumberClick)
        );

        binding().signupVerificationTextExplanation.setText(content);
    }

    private void onDrawerResult(int resultCode) {
        switch (resultCode) {
            case SEND_SMS_ACTION:
                handleResendVerification(VerificationType.SMS);
                break;

            case CALL_ME_ACTION:
                handleResendVerification(VerificationType.CALL);
                break;

            default:
                throw new MissingCaseError(resultCode, "VerificationCode onDrawerResult");
        }
    }

    private void onChangePhoneNumberClick() {
        presenter.changePhoneNumber();
    }

    private void resetCountdownDeadline() {
        deadlineInRealtime = SystemClock.elapsedRealtime() + RESEND_COUNTDOWN_MS;
    }

    private void startCountdown() {
        if (countdownTimer != null) {
            stopCountdown();
        }

        final long remainingMs = deadlineInRealtime - SystemClock.elapsedRealtime();

        // This needs to be BEFORE countdown.start() to handle correctly the case where remainingMs
        // is <= 0 and the countDown immediately finish (as in RIGHT after countdownTimer.start())
        // E.g countdown reaches zero + fragment recreation/rotation change.
        binding().signupVerificationResend.setVisibility(View.GONE);
        binding().signupVerificationCountdown.setVisibility(View.VISIBLE);

        countdownTimer = new MuunCountdownTimer(remainingMs, this);
        countdownTimer.start();
    }

    private void stopCountdown() {
        countdownTimer.cancel();
        countdownTimer = null;
    }

    @Override
    public void handleVerificationCodeError(Throwable error) {
        if (error != null) {
            binding().signupVerificationTextCode.requestFocusInput();
        }
    }

    @Override
    public void onCountDownTick(long remainingSeconds) {
        final long minuteInSeconds = Dates.MINUTE_IN_SECONDS;
        final long minutes = remainingSeconds / minuteInSeconds;
        final long seconds = remainingSeconds % minuteInSeconds;

        final String text = String.format(
                getString(R.string.signup_verification_resend_countdown),
                minutes,
                seconds
        );
        binding().signupVerificationCountdown.setText(text);
    }

    @Override
    public void onCountDownFinish() {
        binding().signupVerificationCountdown.setVisibility(View.GONE);
        binding().signupVerificationResend.setVisibility(View.VISIBLE);
    }
}
