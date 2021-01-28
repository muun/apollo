package io.muun.apollo.presentation.app;

import io.muun.apollo.BuildConfig;
import io.muun.apollo.data.external.Globals;
import io.muun.common.bitcoinj.NetworkParametersHelper;

import android.os.Build;
import org.bitcoinj.core.NetworkParameters;

public class GlobalsImpl extends Globals {

    private final NetworkParameters network = NetworkParametersHelper
            .getNetworkParametersFromName(BuildConfig.NETWORK_NAME);

    @Override
    public String getApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public boolean isDebugBuild() {
        return BuildConfig.DEBUG;
    }

    @Override
    public String getBuildType() {
        return BuildConfig.BUILD_TYPE;
    }

    @Override
    public String getOldBuildType() {
        return BuildConfig.OLD_BUILD_TYPE;
    }

    @Override
    public int getVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    @Override
    public String getVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public NetworkParameters getNetwork() {
        return network;
    }

    @Override
    public String getDeviceName() {
        return Build.DEVICE;
    }

    @Override
    public String getDeviceModel() {
        return Build.MODEL;
    }

    @Override
    public String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }

    @Override
    public String getMuunLinkHost() {
        return BuildConfig.MUUN_LINK_HOST;
    }

    @Override
    public String getVerifyLinkPath() {
        return BuildConfig.VERIFY_LINK_PATH;
    }

    @Override
    public String getAuthorizeLinkPath() {
        return BuildConfig.AUTHORIZE_LINK_PATH;
    }

    @Override
    public String getConfirmLinkPath() {
        return BuildConfig.CONFIRM_LINK_PATH;
    }

    @Override
    public String getRcLoginAuthorizePath() {
        return BuildConfig.RC_LOGIN_AUTHORIZE_LINK_PATH;
    }

}
