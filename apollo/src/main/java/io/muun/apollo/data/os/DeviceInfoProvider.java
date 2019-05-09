package io.muun.apollo.data.os;

import io.muun.apollo.external.Globals;

import javax.inject.Inject;

public class DeviceInfoProvider {

    /**
     * Constructor.
     */
    @Inject
    public DeviceInfoProvider() {
    }

    public String getDeviceName() {
        return Globals.INSTANCE.getDeviceName();
    }

    public String getDeviceModel() {
        return Globals.INSTANCE.getDeviceModel();
    }

    public String getDeviceManufacturer() {
        return Globals.INSTANCE.getDeviceManufacturer();
    }

    public String getApolloVersionName() {
        return "apollo/" + Globals.INSTANCE.getVersionName();
    }

    public int getApolloVersionCode() {
        return Globals.INSTANCE.getVersionCode();
    }
}
