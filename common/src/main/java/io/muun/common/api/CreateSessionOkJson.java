package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateSessionOkJson {

    @NotNull
    public Boolean isExistingUser;

    @NotNull
    public Boolean canUseRecoveryCode;

    @Nullable
    public MuunZonedDateTime passwordSetupDate;

    @Nullable
    public MuunZonedDateTime recoveryCodeSetupDate;

    @Nullable
    public String playIntegrityNonce;

    /**
     * Json constructor.
     */
    public CreateSessionOkJson() {
    }

    /**
     * Full constructor.
     */
    public CreateSessionOkJson(Boolean isExistingUser,
                               Boolean canUseRecoveryCode,
                               @Nullable MuunZonedDateTime passwordSetupDate,
                               @Nullable MuunZonedDateTime recoveryCodeSetupDate,
                               @Nullable String playIntegrityNonce) {

        this.isExistingUser = isExistingUser;
        this.canUseRecoveryCode = canUseRecoveryCode;
        this.passwordSetupDate = passwordSetupDate;
        this.recoveryCodeSetupDate = recoveryCodeSetupDate;
        this.playIntegrityNonce = playIntegrityNonce;
    }

    /**
     * Compat constructor.
     */
    public CreateSessionOkJson(Boolean isExistingUser,
                               Boolean canUseRecoveryCode) {

        this(isExistingUser, canUseRecoveryCode, null, null, null);
    }
}
