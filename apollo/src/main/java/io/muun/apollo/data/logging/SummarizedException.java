package io.muun.apollo.data.logging;

public class SummarizedException extends RuntimeException {

    public SummarizedException(Throwable cause, StackTraceElement[] summarizedStack) {
        super(cause.getClass().getName() + ": " + cause.getMessage());
        setStackTrace(summarizedStack);
    }
}
