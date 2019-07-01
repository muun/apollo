package io.muun.apollo.domain.errors;

import io.muun.common.exception.PotentialBug;

public class InvalidInvoiceException extends RuntimeException implements PotentialBug {

    public InvalidInvoiceException(String invoice, Throwable cause) {
        super(invoice, cause);
    }
}
