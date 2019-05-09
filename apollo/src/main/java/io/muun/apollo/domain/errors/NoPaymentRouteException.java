package io.muun.apollo.domain.errors;

public class NoPaymentRouteException extends RuntimeException {
    public NoPaymentRouteException(Throwable cause) {
        super(cause);
    }
}
