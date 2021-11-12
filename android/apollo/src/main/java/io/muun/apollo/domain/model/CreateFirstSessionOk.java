package io.muun.apollo.domain.model;

import io.muun.apollo.domain.model.user.User;
import io.muun.common.crypto.hd.PublicKey;

public class CreateFirstSessionOk {

    public User user;
    public PublicKey cosigningPublicKey;
    public PublicKey swapServerPublicKey;

    /**
     * Constructor.
     */
    public CreateFirstSessionOk(User user,
                                PublicKey cosigningPublicKey,
                                PublicKey swapServerPublicKey) {
        this.user = user;
        this.cosigningPublicKey = cosigningPublicKey;
        this.swapServerPublicKey = swapServerPublicKey;
    }
}
