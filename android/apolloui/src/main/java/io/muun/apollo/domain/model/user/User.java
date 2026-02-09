package io.muun.apollo.domain.model.user;

import io.muun.apollo.data.preferences.stored.StoredEkVerificationCodes;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.common.Optional;
import io.muun.common.model.Currency;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.utils.Since;

import androidx.annotation.Nullable;
import org.threeten.bp.ZonedDateTime;

import java.util.SortedSet;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

public class User {

    @NotNull
    public final Long hid;

    @NotNull
    public Optional<String> email;
    public boolean isEmailVerified;

    public final Optional<UserPhoneNumber> phoneNumber;
    public final Optional<UserProfile> profile;

    private final CurrencyUnit primaryCurrency;

    public boolean hasRecoveryCode;
    public boolean hasPassword;
    public final boolean hasP2PEnabled;

    @Deprecated
    public boolean hasExportedKeys;

    // Check each EmergencyKit property for @Since values
    public Optional<EmergencyKit> emergencyKit;

    @NotNull // Not backed by Houston, cached locally
    public final StoredEkVerificationCodes emergencyKitVerificationCodes;

    @NotNull
    public final SortedSet<Integer> emergencyKitVersions;

    @Since(apolloVersion = 46)
    public final Optional<ZonedDateTime> createdAt;

    /**
     * Factory method to construct a User from Houston's data.
     */
    public static User fromHouston(@NotNull Long hid,
                                   Optional<String> email,
                                   boolean isEmailVerified,
                                   Optional<UserPhoneNumber> phoneNumber,
                                   Optional<UserProfile> profile,
                                   @NotNull CurrencyUnit primaryCurrency,
                                   boolean hasRecoveryCode,
                                   boolean hasPassword,
                                   boolean hasP2PEnabled,
                                   boolean hasExportedKeys,
                                   Optional<EmergencyKit> emergencyKit,
                                   Optional<ZonedDateTime> createdAt,
                                   @NotNull SortedSet<Integer> emergencyKitVersions) {
        return new User(
                hid,
                email,
                isEmailVerified,
                phoneNumber,
                profile,
                primaryCurrency,
                hasRecoveryCode,
                hasPassword,
                hasP2PEnabled,
                hasExportedKeys,
                emergencyKit,
                new StoredEkVerificationCodes(),
                emergencyKitVersions,
                createdAt
        );
    }

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
                Optional<EmergencyKit> emergencyKit,
                @NotNull StoredEkVerificationCodes emergencyKitVerificationCodes,
                @NotNull SortedSet<Integer> emergencyKitVersions,
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
        this.emergencyKit = emergencyKit;
        this.emergencyKitVerificationCodes = emergencyKitVerificationCodes;
        this.emergencyKitVersions = emergencyKitVersions;

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

    /**
     * Return whether this user has already exported an emergency kit, and thus completed her
     * security setup.
     */
    public boolean hasExportedEmergencyKit() {
        return emergencyKit.isPresent();
    }

    /**
     * Get the user's primary currency, if exchange rate is available, BTC otherwise.
     */
    public CurrencyUnit getPrimaryCurrency(ExchangeRateWindow rateWindow) {
        return getPrimaryCurrency(new ExchangeRateProvider(rateWindow.rates));
    }

    /**
     * Get the user's primary currency, if exchange rate is available, BTC otherwise.
     */
    public CurrencyUnit getPrimaryCurrency(ExchangeRateProvider rateProvider) {
        CurrencyUnit targetCurrency = primaryCurrency;

        if (Currency.OVERRIDES.get(primaryCurrency.getCurrencyCode()) != null) {
            targetCurrency = Currency.OVERRIDES.get(primaryCurrency.getCurrencyCode());
        }

        // Note: DO NOT use rateProvider.isAvailable(CurrencyUnit,CurrencyUnit). Apparently its
        // flawed. It will (strangely) return true when there's no rate for certain currencies.
        if (rateProvider.getCurrencies().contains(targetCurrency)) {
            return targetCurrency;

        } else {
            // TODO this "defaulting" to btc is contrary to our defaulting to usd elsewhere
            // We should probably unify behavior.
            return Currency.getUnit("BTC").get();
        }
    }

    /**
     * Get the user's primary currency, whether or not an exchange rate is available. Careful.
     */
    public CurrencyUnit unsafeGetPrimaryCurrency() {
        return primaryCurrency;
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
