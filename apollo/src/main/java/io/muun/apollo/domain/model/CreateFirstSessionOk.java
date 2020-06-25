package io.muun.apollo.domain.model;

import io.muun.common.crypto.hd.PublicKey;

public class CreateFirstSessionOk {

    public User user;
    public PublicKey cosigningPublicKey;

    /**
     * Constructor.
     */
    public CreateFirstSessionOk(User user, PublicKey cosigningPublicKey) {
        this.user = user;
        this.cosigningPublicKey = cosigningPublicKey;
    }
}
