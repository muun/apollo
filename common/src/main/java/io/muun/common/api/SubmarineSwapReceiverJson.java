package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapReceiverJson {

    @Nullable
    public String alias;

    @NotNull
    public List<String> networkAddresses; // the list may be empty

    @NotNull
    public String publicKey;

    /**
     * JSON constructor.
     */
    public SubmarineSwapReceiverJson() {
    }

    /**
     * Manual constructor.
     */
    public SubmarineSwapReceiverJson(@Nullable String alias,
                                     List<String> networkAddresses,
                                     String publicKey) {
        this.alias = alias;
        this.networkAddresses = networkAddresses;
        this.publicKey = publicKey;
    }
}
