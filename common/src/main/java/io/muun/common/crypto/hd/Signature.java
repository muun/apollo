package io.muun.common.crypto.hd;


public class Signature {

    private final byte[] bytes;

    public Signature(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
