package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Welcome to our Feature Flags! This will represent, hopefully, the many features that we will
 * slowly activate to out users.
 */
public enum MuunFeatureJson {
    @JsonEnumDefaultValue
    UNSUPPORTED_FEATURE,
    TAPROOT,
    TAPROOT_PREACTIVATION,
    APOLLO_BIOMETRICS,
    HIGH_FEES_HOME_BANNER,
    HIGH_FEES_RECEIVE_FLOW,
    EFFECTIVE_FEES_CALCULATION;
}
