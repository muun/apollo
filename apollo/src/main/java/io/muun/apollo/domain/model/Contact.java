package io.muun.apollo.domain.model;

import io.muun.apollo.domain.model.base.HoustonModel;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.utils.Preconditions;

import android.support.annotation.Nullable;

import javax.validation.constraints.NotNull;

public class Contact extends HoustonModel {

    @NotNull
    public final PublicProfile publicProfile;

    @NotNull
    public final int maxAddressVersion;

    @NotNull
    public final PublicKey publicKey;

    @Nullable
    public final PublicKey cosigningPublicKey;

    @NotNull
    public Long lastDerivationIndex;

    /**
     * Constructor.
     */
    public Contact(
            @Nullable Long id,
            @NotNull Long hid,
            @NotNull PublicProfile publicProfile,
            int maxAddressVersion,
            @NotNull PublicKey publicKey,
            @Nullable PublicKey cosigningPublicKey,
            @NotNull Long lastDerivationIndex) {

        super(id, hid);
        this.publicProfile = publicProfile;
        this.maxAddressVersion = maxAddressVersion;
        this.publicKey = publicKey;
        this.cosigningPublicKey = cosigningPublicKey;
        this.lastDerivationIndex = lastDerivationIndex;
    }

    public PublicKeyPair getPublicKeyPair() {
        return new PublicKeyPair(publicKey, cosigningPublicKey);
    }

    /**
     * Merge this Contact with an updated copy, choosing whether to keep or replace each field.
     */
    public Contact mergeWithUpdate(Contact other) {
        Preconditions.checkArgument(hid.equals(other.hid));
        Preconditions.checkArgument(other.id == null || id.equals(other.id));

        return new Contact(
                id,
                hid,
                publicProfile.mergeWithUpdate(other.publicProfile),
                other.maxAddressVersion,
                other.publicKey,
                other.cosigningPublicKey,
                Math.max(lastDerivationIndex, other.lastDerivationIndex)
        );
    }
}
