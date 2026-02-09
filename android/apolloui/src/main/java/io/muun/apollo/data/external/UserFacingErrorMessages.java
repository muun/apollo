package io.muun.apollo.data.external;

import io.muun.apollo.domain.errors.EmptyFieldError;
import io.muun.apollo.domain.model.BiometricAuthenticationErrorReason;

@SuppressWarnings("checkstyle:MissingJavadocMethod")
public abstract class UserFacingErrorMessages {

    /**
     * This will be initialized by front-end code, since it depends on i18n strings defined in
     * the presentation layer.
     */
    public static UserFacingErrorMessages INSTANCE;

    public abstract String invalidPaymentRequest();

    public abstract String tooManyWrongVerificationCodes();

    public abstract String invalidCharacterRecoveryCode();

    public abstract String amountTooSmall();

    public abstract String countryNotSupported();

    public abstract String incorrectRecoveryCode();

    public abstract String insufficientFunds();

    public abstract String expiredSession();

    public abstract String invalidAddress();

    public abstract String deprecatedClientVersion();

    public abstract String incorrectPassword();

    public abstract String invalidVerificationCode();

    public abstract String invalidEmail();

    public abstract String expiredVerificationCode();

    public abstract String emailAreadyUsed();

    public abstract String emailNotRegistered();

    public abstract String invalidPicture();

    public abstract String recoveryCodeVerification();

    public abstract String lnInvoiceNotSupported();

    public abstract String invalidOperationUri();

    public abstract String invalidPhoneNumber();

    public abstract String revokedVerificationCode();

    public abstract String connectivity();

    public abstract String phoneNumberAlreadyUsed();

    public abstract String passwordTooShort();

    public abstract String passwordsDontMatch();

    public abstract String googlePlayServicesNotAvailable();

    public abstract String fcmTokenNotAvailable();

    public abstract String emptyField(EmptyFieldError.Field field);

    public abstract String emergencyKitInvalidVerificationCode();
    
    public abstract String emergencyKitOldVerificationCode(String firstExpectedDigits);

    public abstract String invalidRcV2();

    public abstract String saveEkToDisk();

    public abstract String biometricsAuthenticationError(
            BiometricAuthenticationErrorReason reason
    );
}
