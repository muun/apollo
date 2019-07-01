package io.muun.apollo.domain.errors;


public class InvalidSatelliteQrCodeError extends RuntimeException {

    public InvalidSatelliteQrCodeError(String message) {
        super(message);
    }

    public InvalidSatelliteQrCodeError(String message, Throwable cause) {
        super(message, cause);
    }
}
