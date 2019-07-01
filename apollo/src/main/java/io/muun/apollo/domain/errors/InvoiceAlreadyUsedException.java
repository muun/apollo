package io.muun.apollo.domain.errors;

public class InvoiceAlreadyUsedException extends RuntimeException {
    public InvoiceAlreadyUsedException(String invoice, Throwable cause) {
        super(invoice, cause);
    }
}
