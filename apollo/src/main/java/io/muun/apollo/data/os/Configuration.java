package io.muun.apollo.data.os;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import java.io.IOException;
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Configuration {

    private static final String CONFIG_FILE_NAME = "config.properties";

    private final Properties properties;

    /**
     * Constructor.
     */
    @Inject
    public Configuration(Context context) {

        final Resources resources = context.getResources();
        final AssetManager assetManager = resources.getAssets();

        properties = new Properties();

        try {
            properties.load(assetManager.open(CONFIG_FILE_NAME));
        } catch (IOException e) {
            throw new RuntimeException("Config file '" + CONFIG_FILE_NAME + "' not found.", e);
        }
    }

    public long getLong(String key) {

        return Long.parseLong(properties.getProperty(key));
    }

    public int getInt(String key) {

        return Integer.parseInt(properties.getProperty(key));
    }

    public boolean getBoolean(String key) {

        return Boolean.parseBoolean(properties.getProperty(key));
    }

    public String getString(String key) {

        return properties.getProperty(key);
    }
}
