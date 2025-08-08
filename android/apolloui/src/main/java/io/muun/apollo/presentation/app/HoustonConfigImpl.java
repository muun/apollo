package io.muun.apollo.presentation.app;

import io.muun.apollo.BuildConfig;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.external.HoustonConfig;

import org.bitcoinj.core.NetworkParameters;

public class HoustonConfigImpl implements HoustonConfig {

    @Override
    public String getProtocol() {
        return BuildConfig.HOUSTON_PROTOCOL;
    }

    @Override
    public String getDomain() {
        return BuildConfig.HOUSTON_DOMAIN;
    }

    @Override
    public int getPort() {
        return Integer.parseInt(BuildConfig.HOUSTON_PORT);
    }

    @Override
    public String getPath() {
        return BuildConfig.HOUSTON_PATH;
    }

    @Override
    public String getCertificatePin() {
        return BuildConfig.HOUSTON_CERT_PIN;
    }

    @Override
    public NetworkParameters getNetwork() {
        return Globals.INSTANCE.getNetwork();
    }

    @Override
    public String getUrl() {
        String basePath = getPath();

        if (! basePath.isEmpty()) {
            basePath += "/"; // baseUrl needs a trailing slash
        }

        return String.format(
                "%s://%s:%s/%s",
                getProtocol(),
                getDomain(),
                getPort(),
                basePath
        );
    }
}
