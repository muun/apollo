package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HardwareWalletAddressJson {

    @NotNull
    public String derivationPath;

    @NotNull
    public String address;

    /**
     * Json constructor.
     */
    public HardwareWalletAddressJson() {
    }

    /**
     * Manual constructor.
     */
    public HardwareWalletAddressJson(String derivationPath, String address) {
        this.derivationPath = derivationPath;
        this.address = address;
    }
}
