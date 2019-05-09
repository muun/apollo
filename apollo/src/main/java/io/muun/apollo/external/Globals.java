package io.muun.apollo.external;

import org.bitcoinj.core.NetworkParameters;


public abstract class Globals {

    /**
     * This will be initialized in the UI application code, since it depends on Android build
     * configurations.
     */
    public static Globals INSTANCE;

    public abstract String getApplicationId();

    public abstract boolean isDebugBuild();

    public abstract String getBuildType();

    public abstract String getOldBuildType();

    public abstract int getVersionCode();

    public abstract String getVersionName();

    public abstract String getDeviceName();

    public abstract String getDeviceModel();

    public abstract String getDeviceManufacturer();

    public abstract NetworkParameters getNetwork();
}
