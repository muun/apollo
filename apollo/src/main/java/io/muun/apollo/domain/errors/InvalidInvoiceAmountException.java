package io.muun.apollo.domain.errors;

public class InvalidInvoiceAmountException extends RuntimeException {

    public InvalidInvoiceAmountException(String invoice) {
        super(invoice);
    }

    public InvalidInvoiceAmountException(String invoice, Throwable cause) {
        super(invoice, cause);
    }
}
