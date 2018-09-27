package io.muun.apollo.domain.errors;


public class NullCurrencyBugError extends RuntimeException {

    public NullCurrencyBugError() {
        super("The primary currency preference was found to be null");
    }
}
