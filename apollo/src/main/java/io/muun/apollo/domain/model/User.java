package io.muun.apollo.domain.model;

import io.muun.common.Optional;
import io.muun.common.utils.Since;

import androidx.annotation.Nullable;
import org.threeten.bp.ZonedDateTime;

import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

public class User {

    @NotNull
    public final Long hid;

    @NotNull
    public final Optional<String> email;
    public final boolean isEmailVerified;

    public final Optional<UserPhoneNumber> phoneNumber;
    public final Optional<UserProfile> profile;

    public final CurrencyUnit primaryCurrency;

    public final boolean hasRecoveryCode;
    public final boolean hasP2PEnabled;

    @Nullable
    @Since(apolloVersion = 46)
    public ZonedDateTime createdAt;

    /**
     * Constructor.
     */
    public User(@NotNull Long hid,
                Optional<String> email,
                boolean isEmailVerified,
                Optional<UserPhoneNumber> phoneNumber,
                Optional<UserProfile> profile,
                @NotNull CurrencyUnit primaryCurrency,
                boolean hasRecoveryCode,
                boolean hasP2PEnabled,
                ZonedDateTime createdAt) {

        this.hid = hid;

        this.email = email;
        this.isEmailVerified =  isEmailVerified;

        this.phoneNumber = phoneNumber;
        this.profile = profile;
        this.primaryCurrency = primaryCurrency;

        this.hasRecoveryCode = hasRecoveryCode;
        this.hasP2PEnabled = hasP2PEnabled;

        this.createdAt = createdAt;
    }

    /**
     * Return the PublicProfile for this User, for compatibility with older code.
     */
    @Nullable
    public PublicProfile getCompatPublicProfile() {
        return profile
                .map(profile -> new PublicProfile(
                        null,
                        hid,
                        profile.getFirstName(),
                        profile.getLastName(),
                        profile.getPictureUrl()
                ))
                .orElse(null);
    }
}
