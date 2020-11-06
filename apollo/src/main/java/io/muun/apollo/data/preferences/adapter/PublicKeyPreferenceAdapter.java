package io.muun.apollo.data.preferences.adapter;

import io.muun.apollo.data.preferences.rx.Preference;
import io.muun.common.crypto.hd.PublicKey;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import javax.annotation.Nullable;

public class PublicKeyPreferenceAdapter implements Preference.Adapter<PublicKey> {

    public static final PublicKeyPreferenceAdapter INSTANCE = new PublicKeyPreferenceAdapter();

    @Override
    @Nullable
    public PublicKey get(@NonNull String prefName, @NonNull SharedPreferences prefs) {
        final String base58 = prefs.getString(getBase58Key(prefName), null);
        final String path = prefs.getString(getPathKey(prefName), null);

        if (base58 == null || path == null) {
            return null;
        }

        return PublicKey.deserializeFromBase58(path, base58);
    }

    @Override
    public void set(@NonNull String prefName,
                    @NonNull PublicKey value,
                    @NonNull SharedPreferences.Editor editor) {

        final String base58 = value.serializeBase58();
        final String path = value.getAbsoluteDerivationPath();

        editor.putString(getBase58Key(prefName), base58);
        editor.putString(getPathKey(prefName), path);
    }

    private String getBase58Key(String prefName) {
        return prefName; // we need to use `prefName`, or .contains() will return false
    }

    private String getPathKey(String prefName) {
        return prefName + "_path";
    }
}