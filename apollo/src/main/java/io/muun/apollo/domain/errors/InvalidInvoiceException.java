package io.muun.apollo.domain.errors;

public class InvalidInvoiceException extends RuntimeException {

    public InvalidInvoiceException() {
        super();
    }

    public InvalidInvoiceException(Throwable cause) {
        super(cause);
    }
}
