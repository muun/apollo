package io.muun.apollo.domain.errors;

public class IntentFailedError extends RuntimeException {

    private int resultCode;

    public IntentFailedError(int resultCode) {
        super("Intent failed with result code: " + resultCode);
        this.resultCode = resultCode;
    }

    public int getResultCode() {
        return resultCode;
    }
}
