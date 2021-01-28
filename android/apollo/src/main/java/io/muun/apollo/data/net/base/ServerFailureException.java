package io.muun.apollo.data.net.base;


public class ServerFailureException extends RuntimeException {
    public ServerFailureException(Throwable cause) {
        super("We're facing a temporary issue. Please, try again later", cause);
    }
}
