package io.muun.apollo.presentation.ui.bundler;

import io.muun.apollo.data.serialization.SerializationUtils;

import android.os.Bundle;
import icepick.Bundler;

import java.util.List;

public class StringListBundler implements Bundler<List<String>>  {

    @Override
    public void put(String key, List<String> list, Bundle bundle) {
        bundle.putString(key, SerializationUtils.serializeList(String.class, list));
    }

    @Override
    public List<String> get(String key, Bundle bundle) {
        final String value = bundle.getString(key);

        if (value != null) {
            return SerializationUtils.deserializeList(String.class, value);
        } else {
            return null;
        }
    }
}
