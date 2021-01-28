package io.muun.apollo.presentation.ui.bundler;

import io.muun.apollo.domain.model.CountryInfo;

import android.os.Bundle;
import icepick.Bundler;

public class CountryInfoBundler implements Bundler<CountryInfo> {
    @Override
    public void put(String key, CountryInfo info, Bundle bundle) {
        bundle.putString(key, info != null ? info.countryCode : null);
    }

    @Override
    public CountryInfo get(String key, Bundle bundle) {
        final String countryCode = bundle.getString(key);

        if (countryCode == null) {
            return null;
        }

        return CountryInfo.findByCode(countryCode).orElse(null);
    }
}