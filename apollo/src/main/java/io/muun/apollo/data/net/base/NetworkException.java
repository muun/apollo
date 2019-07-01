package io.muun.apollo.data.net.base;

import io.muun.common.exception.PotentialBug;

public class NetworkException extends RuntimeException implements PotentialBug {

    public NetworkException(String url, Throwable cause) {
        super("Can't reach " + url, cause);
    }

    public NetworkException(Throwable cause) {
        super("Can't reach the remote server", cause);
    }
}
