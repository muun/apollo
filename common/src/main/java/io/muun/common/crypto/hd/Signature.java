package io.muun.common.crypto.hd;


import io.muun.common.api.SignatureJson;
import io.muun.common.utils.Encodings;

public class Signature {

    private final byte[] bytes;

    public static Signature fromJson(SignatureJson json) {
        return new Signature(Encodings.hexToBytes(json.hex));
    }

    public Signature(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public SignatureJson toJson() {
        return new SignatureJson(Encodings.bytesToHex(bytes));
    }
}
