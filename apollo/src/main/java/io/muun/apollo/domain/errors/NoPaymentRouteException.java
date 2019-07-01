package io.muun.apollo.domain.errors;

import io.muun.common.exception.PotentialBug;

public class NoPaymentRouteException extends RuntimeException implements PotentialBug {
    public NoPaymentRouteException(String invoice, Throwable cause) {
        super(invoice, cause);
    }
}
