package io.muun.apollo.domain.errors;

public class InvoiceExpiresTooSoonException extends RuntimeException {

    public InvoiceExpiresTooSoonException(Throwable cause) {
        super(cause);
    }
}
