package io.muun.apollo.data.net.base;

public class NetworkException extends RuntimeException {

    public NetworkException(Throwable throwable) {
        super("Can't reach the Muun server", throwable);
    }
}
