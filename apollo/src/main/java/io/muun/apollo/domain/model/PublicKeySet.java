package io.muun.apollo.domain.model;


import io.muun.common.crypto.hd.PublicKeyPair;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class PublicKeySet {

    @NotNull
    private final PublicKeyPair basePublicKeyPair;

    @NotNull
    public Integer externalMaxUsedIndex;

    @Nullable
    public Integer externalMaxWatchingIndex;

    /**
     * Constructor.
     */
    public PublicKeySet(PublicKeyPair basePublicKeyPair,
                        Integer externalMaxUsedIndex,
                        Integer externalMaxWatchingIndex) {

        this.basePublicKeyPair = basePublicKeyPair;
        this.externalMaxUsedIndex = externalMaxUsedIndex;
        this.externalMaxWatchingIndex = externalMaxWatchingIndex;
    }

    public PublicKeyPair getBasePublicKeyPair() {
        return basePublicKeyPair;
    }

    public Integer getExternalMaxUsedIndex() {
        return externalMaxUsedIndex;
    }

    @Nullable
    public Integer getExternalMaxWatchingIndex() {
        return externalMaxWatchingIndex;
    }
}
