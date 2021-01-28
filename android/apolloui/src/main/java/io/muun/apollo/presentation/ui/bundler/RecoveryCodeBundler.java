package io.muun.apollo.presentation.ui.bundler;


import io.muun.apollo.domain.libwallet.RecoveryCodeV2;

import android.os.Bundle;
import icepick.Bundler;

public class RecoveryCodeBundler implements Bundler<RecoveryCodeV2> {

    @Override
    public void put(String key, RecoveryCodeV2 recoveryCode, Bundle bundle) {
        if (recoveryCode != null) {
            bundle.putString(key, recoveryCode.toString());
        }
    }

    @Override
    public RecoveryCodeV2 get(String key, Bundle bundle) {
        final String recoveryCodeString = bundle.getString(key);

        if (recoveryCodeString != null) {
            return RecoveryCodeV2.fromString(recoveryCodeString);
        } else {
            return null;
        }
    }
}
