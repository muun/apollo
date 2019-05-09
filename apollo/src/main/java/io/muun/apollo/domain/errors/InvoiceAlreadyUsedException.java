package io.muun.apollo.domain.errors;

public class InvoiceAlreadyUsedException extends RuntimeException {
    public InvoiceAlreadyUsedException(Throwable cause) {
        super(cause);
    }
}
