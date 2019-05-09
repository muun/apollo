package io.muun.apollo.external;

import org.bitcoinj.core.NetworkParameters;

public interface HoustonConfig {

    String getProtocol();

    String getDomain();

    int getPort();

    String getPath();

    String getUrl();

    String getCertificatePin();

    NetworkParameters getNetwork();
}
