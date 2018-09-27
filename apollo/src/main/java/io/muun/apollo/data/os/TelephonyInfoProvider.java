package io.muun.apollo.data.os;

import io.muun.common.Optional;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.util.Locale;

import javax.inject.Inject;

public class TelephonyInfoProvider {

    private final TelephonyManager telephonyManager;

    @Inject
    public TelephonyInfoProvider(Context context) {
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * Get the ISO country code (2-letter code) for the current network.
     */
    public Optional<String> getRegion() {

        final String countryIso = telephonyManager.getNetworkCountryIso();

        // This value is unavailable if the user isn't registered to a network, and may be
        // unreliable on CDMA networks.
        if (countryIso == null || countryIso.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(countryIso.toUpperCase(Locale.US));
    }

}
