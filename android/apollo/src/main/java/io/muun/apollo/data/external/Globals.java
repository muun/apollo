package io.muun.apollo.data.external;

import org.bitcoinj.core.NetworkParameters;


public abstract class Globals {

    /**
     * This will be initialized in the UI application code, since it depends on Android build
     * configurations.
     */
    public static Globals INSTANCE;

    /**
     * Get the Application Id (previously package name) of the app. Identifies the app on the
     * device, its unique in the Google Play store.
     */
    public abstract String getApplicationId();

    /**
     * Get whether the current build a debuggable build.
     */
    public abstract boolean isDebugBuild();

    /**
     * Get the build type of the current build.
     */
    public abstract String getBuildType();

    /**
     * Get the legacy build type of the current build. It is now deprecated in favour of
     * {@link Globals#getBuildType()}.
     */
    public abstract String getOldBuildType();

    /**
     * Get the version code of the current build (e.g 1004).
     */
    public abstract int getVersionCode();

    /**
     * Get the version name of the current build (e.g 50.4).
     */
    public abstract String getVersionName();

    /**
     * Get the version name of the current build (e.g 50.4).
     */
    public abstract String getDeviceName();

    /**
     * Get the model name of the device where app is running.
     */
    public abstract String getDeviceModel();

    /**
     * Get the manufacturer name of the device where app is running.
     */
    public abstract String getDeviceManufacturer();

    /**
     * Get the fingerprint of the device where app is running.
     */
    public abstract String getFingerprint();

    /**
     * Get the hardware name of the device where app is running.
     */
    public abstract String getHardware();

    /**
     * Get the bootloader name of the device where app is running.
     */
    public abstract String getBootloader();

    /**
     * Get the bitcoin network specs/parameters of the network this build is using.
     */
    public abstract NetworkParameters getNetwork();

    /**
     * Get the hostname of this app's deeplink.
     */
    public abstract String getMuunLinkHost();

    /**
     * Get the path of this app's "Verify" deeplink.
     */
    public abstract String getVerifyLinkPath();

    /**
     * Get the path of this app's "Authorize" deeplink.
     */
    public abstract String getAuthorizeLinkPath();

    /**
     * Get the path of this app's "Confirm" deeplink.
     */
    public abstract String getConfirmLinkPath();

    /**
     * Get the path of this app's "Authorize RC Login" deeplink.
     */
    public abstract String getRcLoginAuthorizePath();

    /**
     * Get whether the current build is a release build.
     */
    public boolean isReleaseBuild() {
        return getBuildType().equals("release");
    }
}
