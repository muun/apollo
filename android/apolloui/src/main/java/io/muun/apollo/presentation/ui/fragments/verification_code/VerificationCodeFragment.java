package io.muun.apollo.presentation.ui.fragments.verification_code;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.MuunCountdownTimer;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.DrawerDialogFragment;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunTextInput;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.VerificationType;

import android.content.Intent;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDialogFragment;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.OnClick;
import icepick.State;

import static io.muun.common.utils.Dates.MINUTE_IN_SECONDS;

public class VerificationCodeFragment extends SingleFragment<VerificationCodePresenter>
        implements VerificationCodeView {

    private static final int DRAWER_REQUEST = 1;
    private static final int SEND_SMS_ACTION = 2;
    private static final int CALL_ME_ACTION = 3;

    private static final int RESEND_COUNTDOWN_MS = 90 * 1000;

    @BindView(R.id.signup_verification_text_explanation)
    HtmlTextView explanation;

    @BindView(R.id.signup_verification_text_code)
    MuunTextInput verificationCode;

    @BindView(R.id.signup_continue)
    MuunButton continueButton;

    @BindView(R.id.signup_verification_resend)
    TextView resendButton;

    @BindView(R.id.signup_verification_countdown)
    TextView countdownText;

    @BindString(R.string.signup_verification_resend_countdown)
    String countdownTextFormat;

    @BindString(R.string.action_resend_sms)
    String resendSmsLabel;

    @BindString(R.string.action_call_me)
    String callMeLabel;

    @BindString(R.string.action_change_num)
    String changeNumberLinkText;

    @State
    long deadlineInRealtime = 0; // time when resendButton is re-enabled

    private CountDownTimer countdownTimer;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.signup_verification_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        verificationCode.setOnChangeListener(newText -> {
            final boolean enable = !TextUtils.isEmpty(newText) && newText.length() >= 6;
            continueButton.setEnabled(enable);
            verificationCode.setOnKeyboardNextListener(enable ? continueButton::callOnClick : null);
        });

        continueButton.setEnabled(false);

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
        verificationCode.requestFocusInput();
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
        verificationCode.setEnabled(!isLoading);
        continueButton.setLoading(isLoading);
    }

    @OnClick(R.id.signup_verification_resend)
    void onResendButtonClick() {
        final AppCompatDialogFragment drawer = new DrawerDialogFragment()
                .addAction(SEND_SMS_ACTION, R.drawable.ic_gallery_24_px, resendSmsLabel)
                .addAction(CALL_ME_ACTION, R.drawable.ic_camera_24_px, callMeLabel);

        requestExternalResult(DRAWER_REQUEST, drawer);
    }

    @OnClick(R.id.signup_continue)
    void onContinueButtonClick() {
        presenter.submitVerificationCode(verificationCode.getText().toString());
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
                new RichText(changeNumberLinkText).setLink(this::onChangePhoneNumberClick)
        );

        explanation.setText(content);
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
        resendButton.setVisibility(View.GONE);
        countdownText.setVisibility(View.VISIBLE);

        countdownTimer = new ResendCountdownTimer(remainingMs);
        countdownTimer.start();
    }

    private void stopCountdown() {
        countdownTimer.cancel();
        countdownTimer = null;
    }

    @Override
    public void handleVerificationCodeError(Throwable error) {
        if (error != null) {
            verificationCode.requestFocusInput();
        }
    }

    private class ResendCountdownTimer extends MuunCountdownTimer {

        ResendCountdownTimer(long durationInMillis) {
            super(durationInMillis);
        }

        @Override
        public void onTickSeconds(long remainingSeconds) {
            final long minutes = remainingSeconds / MINUTE_IN_SECONDS;
            final long seconds = remainingSeconds % MINUTE_IN_SECONDS;

            final String text = String.format(countdownTextFormat, minutes, seconds);
            countdownText.setText(text);
        }

        @Override
        public void onFinish() {
            countdownText.setVisibility(View.GONE);
            resendButton.setVisibility(View.VISIBLE);
        }
    }
}
