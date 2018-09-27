package io.muun.apollo.domain.errors;

public class InitialSyncError extends RuntimeException {

    /**
     * Construct an InitialSyncError. There, now you understand. Long live mandatory Javadoc!
     */
    public InitialSyncError(Throwable cause) {
        super(
                "We couldn't load your information. Please, restart the application and try again",
                cause
        );
    }
}
