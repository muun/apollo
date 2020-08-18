package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.money.CurrencyUnit;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserJson {

    public Long id;

    @Nullable
    public String email;

    public boolean isEmailVerified;
    public boolean hasExportedKeys;

    @Nullable
    public MuunZonedDateTime emergencyKitLastExportedAt;

    public PublicProfileJson publicProfile;
    public PhoneNumberJson phoneNumber;

    public CurrencyUnit primaryCurrency;

    public boolean hasPasswordChallengeKey;
    public boolean hasRecoveryCodeChallengeKey;

    public boolean hasP2PEnabled;

    @Since(
            apolloVersion = Supports.CreationDateInUserInfo.APOLLO,
            falconVersion = Supports.CreationDateInUserInfo.FALCON
    )
    public MuunZonedDateTime createdAt;

    /**
     * Json constructor.
     */
    public UserJson() {
    }

    /**
     * Houston constructor.
     */
    public UserJson(Long id,
                    @Nullable String email,
                    boolean isEmailVerified,
                    boolean hasExportedKeys,
                    @Nullable MuunZonedDateTime emergencyKitLastExportedAt,
                    PublicProfileJson publicProfile,
                    PhoneNumberJson phoneNumber,
                    CurrencyUnit primaryCurrency,
                    boolean hasPasswordChallengeKey,
                    boolean hasRecoveryCodeChallengeKey,
                    boolean hasP2PEnabled,
                    MuunZonedDateTime createdAt) {

        this.id = id;
        this.email = email;
        this.isEmailVerified = isEmailVerified;
        this.hasExportedKeys = hasExportedKeys;
        this.emergencyKitLastExportedAt = emergencyKitLastExportedAt;
        this.publicProfile = publicProfile;
        this.phoneNumber = phoneNumber;
        this.primaryCurrency = primaryCurrency;
        this.hasPasswordChallengeKey = hasPasswordChallengeKey;
        this.hasRecoveryCodeChallengeKey = hasRecoveryCodeChallengeKey;
        this.hasP2PEnabled = hasP2PEnabled;
        this.createdAt = createdAt;
    }
}
