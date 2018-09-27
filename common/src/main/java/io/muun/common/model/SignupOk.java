package io.muun.common.model;


import io.muun.common.crypto.hd.PublicKey;

public class SignupOk {

    private final PublicKey muunPublicKey;

    public SignupOk(PublicKey muunPublicKey) {
        this.muunPublicKey = muunPublicKey;
    }

    public PublicKey getMuunPublicKey() {
        return muunPublicKey;
    }
}
