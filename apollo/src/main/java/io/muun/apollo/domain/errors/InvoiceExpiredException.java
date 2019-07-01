package io.muun.apollo.domain.errors;

public class InvoiceExpiredException extends RuntimeException {

    public InvoiceExpiredException(String invoice) {
        super(invoice);
    }
}
