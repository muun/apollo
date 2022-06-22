package io.muun.apollo.presentation.ui.utils;

import io.muun.apollo.R;
import io.muun.apollo.data.external.UserFacingErrorMessages;
import io.muun.apollo.domain.errors.EmptyFieldError;
import io.muun.common.exception.MissingCaseError;

import android.content.Context;
import androidx.annotation.StringRes;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UserFacingErrorMessagesImpl extends UserFacingErrorMessages {

    private final Context context;

    @Inject
    public UserFacingErrorMessagesImpl(Context context) {
        this.context = context;
    }

    @Override
    public String invalidPaymentRequest() {
        return get(R.string.error_invalid_payment_request);
    }

    @Override
    public String tooManyWrongVerificationCodes() {
        return get(R.string.error_verification_code_limit_reached);
    }

    @Override
    public String invalidCharacterRecoveryCode() {
        return get(R.string.recovery_code_error_invalid_character);
    }

    @Override
    public String amountTooSmall() {
        return get(R.string.error_amount_too_small);
    }

    @Override
    public String countryNotSupported() {
        return get(R.string.error_country_not_supported);
    }

    @Override
    public String incorrectRecoveryCode() {
        return get(R.string.error_incorrect_recovery_code);
    }

    @Override
    public String insufficientFunds() {
        return get(R.string.error_insufficient_funds);
    }

    @Override
    public String expiredSession() {
        return get(R.string.error_expired_session);
    }

    @Override
    public String invalidAddress() {
        return get(R.string.error_invalid_address);
    }

    @Override
    public String deprecatedClientVersion() {
        return get(R.string.error_deprecated_client_version);
    }

    @Override
    public String incorrectPassword() {
        return get(R.string.error_incorrect_password);
    }

    @Override
    public String invalidVerificationCode() {
        return get(R.string.error_invalid_verification_code);
    }

    @Override
    public String invalidEmail() {
        return get(R.string.error_invalid_email_address);
    }

    @Override
    public String expiredVerificationCode() {
        return get(R.string.error_verification_code_expired);
    }

    @Override
    public String emailAreadyUsed() {
        return get(R.string.error_email_already_used);
    }

    @Override
    public String emailNotRegistered() {
        return get(R.string.error_email_not_registered);
    }

    @Override
    public String invalidPicture() {
        return get(R.string.error_invalid_picture);
    }

    @Override
    public String recoveryCodeVerification() {
        return get(R.string.error_recovery_code_verification);
    }

    @Override
    public String lnInvoiceNotSupported() {
        return get(R.string.error_ln_invoice_not_supported);
    }

    @Override
    public String invalidOperationUri() {
        return get(R.string.error_invalid_operation_uri);
    }

    @Override
    public String invalidPhoneNumber() {
        return get(R.string.error_invalid_phone_number);
    }

    @Override
    public String revokedVerificationCode() {
        return get(R.string.error_revoked_verification_code);
    }

    @Override
    public String connectivity() {
        return get(R.string.error_network_error);
    }

    @Override
    public String phoneNumberAlreadyUsed() {
        return get(R.string.error_phone_number_already_used);
    }

    @Override
    public String passwordTooShort() {
        return get(R.string.error_password_too_short);
    }

    @Override
    public String passwordsDontMatch() {
        return get(R.string.error_passwords_dont_match);
    }

    @Override
    public String googlePlayServicesNotAvailable() {
        return get(R.string.google_play_services_not_available);
    }

    @Override
    public String fcmTokenNotAvailable() {
        return get(R.string.fcm_token_not_available);
    }

    @Override
    public String emergencyKitInvalidVerificationCode() {
        return get(R.string.ek_verify_failed);
    }

    @Override
    public String emergencyKitOldVerificationCode(String firstExpectedDigits) {
        return get(R.string.ek_verify_old, firstExpectedDigits);
    }

    @Override
    public String emptyField(EmptyFieldError.Field field) {
        final @StringRes int fieldName;

        switch (field) {
            case PASSWORD:
                fieldName = R.string.field_password;
                break;

            case FIRST_NAME:
                fieldName = R.string.field_first_name;
                break;

            case LAST_NAME:
                fieldName = R.string.field_last_name;
                break;

            default:
                throw new MissingCaseError(field);
        }

        return get(R.string.error_field_required, get(fieldName));
    }

    @Override
    public String invalidRcV2() {
        return get(R.string.rc_only_login_error);
    }

    @Override
    public String saveEkToDisk() {
        return get(R.string.ek_emergency_kit_save_failure);
    }

    private String get(@StringRes int resId, Object... args) {
        return context.getString(resId, args);
    }
}
