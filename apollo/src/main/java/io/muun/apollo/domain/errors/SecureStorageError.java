package io.muun.apollo.domain.errors;

public class SecureStorageError extends RuntimeException {
    public SecureStorageError(Throwable throwable) {
        super(throwable);
    }

    public SecureStorageError() {
    }
}
