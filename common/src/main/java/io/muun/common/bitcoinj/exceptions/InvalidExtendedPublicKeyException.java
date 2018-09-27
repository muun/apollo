package io.muun.common.bitcoinj.exceptions;

public class InvalidExtendedPublicKeyException extends RuntimeException {

    public InvalidExtendedPublicKeyException(String key) {
        super("Invalid extended public key: " + key);
    }

}
