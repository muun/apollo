package io.muun.apollo.domain.model;

import io.muun.common.Optional;

import android.support.annotation.Nullable;

import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

public class User {

    @NotNull
    public final Long hid;

    @NotNull
    public final String email;
    public final boolean isEmailVerified;

    public final Optional<UserPhoneNumber> phoneNumber;
    public final Optional<UserProfile> profile;

    public final CurrencyUnit primaryCurrency;

    public final boolean hasRecoveryCode;
    public final boolean hasP2PEnabled;

    /**
     * Constructor.
     */
    public User(@NotNull Long hid,
                @NotNull String email,
                boolean isEmailVerified,
                Optional<UserPhoneNumber> phoneNumber,
                Optional<UserProfile> profile,
                @NotNull CurrencyUnit primaryCurrency,
                boolean hasRecoveryCode,
                boolean hasP2PEnabled) {

        this.hid = hid;

        this.email = email;
        this.isEmailVerified =  isEmailVerified;

        this.phoneNumber = phoneNumber;
        this.profile = profile;
        this.primaryCurrency = primaryCurrency;

        this.hasRecoveryCode = hasRecoveryCode;
        this.hasP2PEnabled = hasP2PEnabled;
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
