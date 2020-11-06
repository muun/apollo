package io.muun.common.api;

import io.muun.common.utils.Deprecated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapFeesJson {

    @NotNull
    public Long lightningInSats = 0L;

    @NotNull
    public Long sweepInSats;

    @Deprecated(atApolloVersion = 76)
    @Nullable   // Since HWs deprecation, this can be null
    public Long channelOpenInSats;

    @Deprecated(atApolloVersion = 76)
    @Nullable   // Since HWs deprecation, this can be null
    public Long channelCloseInSats;

    /**
     * Json constructor.
     */
    public SubmarineSwapFeesJson() {
    }

    /**
     * Apollo Constructor.
     */
    public SubmarineSwapFeesJson(long lightningInSats, long sweepInSats) {

        this.lightningInSats = lightningInSats;
        this.sweepInSats = sweepInSats;
    }

    /**
     * Houston Constructor.
     */
    public SubmarineSwapFeesJson(long lightningInSats,
                                 long sweepInSats,
                                 long channelOpenInSats,
                                 long channelCloseInSats) {

        this.lightningInSats = lightningInSats;
        this.sweepInSats = sweepInSats;
        this.channelOpenInSats = channelOpenInSats;
        this.channelCloseInSats = channelCloseInSats;
    }
}
