package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapFeesJson {

    @NotNull
    public Long lightningInSats = 0L;

    @NotNull
    public Long sweepInSats;

    @NotNull
    public Long channelOpenInSats = 0L;

    @NotNull
    public Long channelCloseInSats = 0L;

    /**
     * Json constructor.
     */
    public SubmarineSwapFeesJson() {
    }


    /**
     * Constructor.
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
