package io.muun.apollo.domain.model;

import io.muun.apollo.domain.model.base.HoustonIdModel;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.utils.Preconditions;

import androidx.annotation.Nullable;

import javax.validation.constraints.NotNull;

public class Contact extends HoustonIdModel {

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
            @NotNull long hid,
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

    /**
     * Merge this Contact with an updated copy, choosing whether to keep or replace each field.
     */
    public Contact mergeWithUpdate(Contact other) {
        Preconditions.checkArgument(getHid() == other.getHid());
        Preconditions.checkArgument(other.getId() == null || getId().equals(other.getId()));

        return new Contact(
                getId(),
                getHid(),
                publicProfile.mergeWithUpdate(other.publicProfile),
                other.maxAddressVersion,
                other.publicKey,
                other.cosigningPublicKey,
                Math.max(lastDerivationIndex, other.lastDerivationIndex)
        );
    }

    /**
     * Convert to a json-serializable representation.
     */
    public io.muun.common.api.Contact toJson() {
        return new io.muun.common.api.Contact(
                publicProfile.toJson(),
                maxAddressVersion,
                publicKey.toJson(),
                cosigningPublicKey != null ? cosigningPublicKey.toJson() : null,
                lastDerivationIndex
        );
    }

    /**
     * Build from a json-serializable representation.
     */
    @Nullable
    public static Contact fromJson(io.muun.common.api.Contact contact) {
        if (contact == null) {
            return null;
        }

        return new Contact(
                null,
                contact.publicProfile.userId,
                PublicProfile.fromJson(contact.publicProfile),
                contact.maxAddressVersion,
                PublicKey.fromJson(contact.publicKey),
                PublicKey.fromJson(contact.cosigningPublicKey),
                contact.lastDerivationIndex
        );
    }
}
