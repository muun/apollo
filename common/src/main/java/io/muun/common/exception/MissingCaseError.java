package io.muun.common.exception;

public class MissingCaseError extends RuntimeException implements PotentialBug {

    public MissingCaseError(Enum<?> enumItem) {
        this(enumItem.name(), enumItem.getClass().getSimpleName());
    }

    public MissingCaseError(Object value, String domain) {
        super("Switch on " + domain + " had no case for " + value);
    }
}
