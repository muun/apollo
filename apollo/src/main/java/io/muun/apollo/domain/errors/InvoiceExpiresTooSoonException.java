package io.muun.apollo.domain.errors;

public class InvoiceExpiresTooSoonException extends RuntimeException {

    public InvoiceExpiresTooSoonException(String invoice, Throwable cause) {
        super(invoice, cause);
    }
}
