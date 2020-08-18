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
    public Optional<String> email;
    public boolean isEmailVerified;

    public Optional<UserPhoneNumber> phoneNumber;
    public Optional<UserProfile> profile;

    public CurrencyUnit primaryCurrency;

    public boolean hasRecoveryCode;
    public boolean hasPassword;
    public final boolean hasP2PEnabled;

    @Deprecated
    public boolean hasExportedKeys;

    @Since(apolloVersion = 72)
    public Optional<ZonedDateTime> emergencyKitLastExportedAt;

    @Since(apolloVersion = 46)
    public Optional<ZonedDateTime> createdAt;

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
                boolean hasPassword,
                boolean hasP2PEnabled,
                boolean hasExportedKeys,
                Optional<ZonedDateTime> emergencyKitLastExportedAt,
                Optional<ZonedDateTime> createdAt) {

        this.hid = hid;

        this.email = email;
        this.isEmailVerified =  isEmailVerified;

        this.phoneNumber = phoneNumber;
        this.profile = profile;
        this.primaryCurrency = primaryCurrency;

        this.hasRecoveryCode = hasRecoveryCode;
        this.hasPassword = hasPassword;
        this.hasP2PEnabled = hasP2PEnabled;
        this.hasExportedKeys = hasExportedKeys;
        this.emergencyKitLastExportedAt = emergencyKitLastExportedAt;

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

    public boolean isRecoverable() {
        return hasPassword || hasRecoveryCode;
    }

    public boolean hasExportedEmergencyKit() {
        return emergencyKitLastExportedAt.isPresent();
    }

    /**
     * Get the support ID string for this user, provided it can be computed with the data available
     * at this version / for this user.
     */
    public Optional<String> getSupportId() {
        if (!createdAt.isPresent()) {
            return Optional.empty(); // missing? it wasn't added until a later version
        }

        // Epoch timestamp as numeric string:
        final String ts = "" + createdAt.get().toEpochSecond();
        final int tsLen = ts.length();

        // Last 2 groups of 4 characters each:
        final String group1 = ts.substring(tsLen - 8, tsLen - 4);
        final String group2 = ts.substring(tsLen - 4, tsLen);

        // Joined by a dash:
        final String supportId = group1 + "-" + group2;

        return Optional.of(supportId);
    }
}
