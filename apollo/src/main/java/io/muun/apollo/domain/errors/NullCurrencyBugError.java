package io.muun.apollo.domain.errors;


import io.muun.common.exception.PotentialBug;

public class NullCurrencyBugError extends RuntimeException implements PotentialBug {

    public NullCurrencyBugError() {
        super("The primary currency preference was found to be null");
    }
}
