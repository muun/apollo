package io.muun.common.exception;

public class ParsingException extends IllegalArgumentException {

    public ParsingException(String description) {
        super("Fail to parse: " + description);
    }
}
