package io.muun.apollo.domain.errors;

import io.muun.common.exception.PotentialBug;

public class InvalidSwapException extends RuntimeException implements PotentialBug {

    public InvalidSwapException(String swapUuid) {
        super("Validation failed for swap UUID " + swapUuid);
    }
}
