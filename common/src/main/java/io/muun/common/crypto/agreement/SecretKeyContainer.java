package io.muun.common.crypto.agreement;

import io.muun.common.crypto.ChallengePublicKey;

import javax.crypto.SecretKey;

public class SecretKeyContainer {
    public final SecretKey sharedSecretKey;
    public final ChallengePublicKey publicKey;

    public SecretKeyContainer(SecretKey sharedSecretKey, ChallengePublicKey publicKey) {
        this.sharedSecretKey = sharedSecretKey;
        this.publicKey = publicKey;
    }
}
