package io.muun.apollo.domain.model;


import io.muun.common.crypto.hd.PublicKeyTriple;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

public class PublicKeySet {

    @NotNull
    private final PublicKeyTriple basePublicKeyTriple;

    @NotNull
    public final Integer externalMaxUsedIndex;

    @Nullable
    public final Integer externalMaxWatchingIndex;

    /**
     * Constructor.
     */
    public PublicKeySet(PublicKeyTriple basePublicKeyTriple,
                        Integer externalMaxUsedIndex,
                        Integer externalMaxWatchingIndex) {

        this.basePublicKeyTriple = basePublicKeyTriple;
        this.externalMaxUsedIndex = externalMaxUsedIndex;
        this.externalMaxWatchingIndex = externalMaxWatchingIndex;
    }

    public PublicKeyTriple getBasePublicKeyTriple() {
        return basePublicKeyTriple;
    }

    public Integer getExternalMaxUsedIndex() {
        return externalMaxUsedIndex;
    }

    @Nullable
    public Integer getExternalMaxWatchingIndex() {
        return externalMaxWatchingIndex;
    }
}
