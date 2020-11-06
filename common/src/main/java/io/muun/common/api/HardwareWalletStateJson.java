package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HardwareWalletStateJson {

    @NotNull
    public List<SizeForAmountJson> sizeForAmount;

    @NotNull
    public List<HardwareWalletOutputJson> sortedUtxos;

    @NotNull
    public HardwareWalletAddressJson changeAddress;

    @NotNull
    public HardwareWalletAddressJson nextAddress;

    /**
     * Manual constructor.
     */
    public HardwareWalletStateJson(
            List<HardwareWalletOutputJson> sortedUtxos,
            List<SizeForAmountJson> sizeForAmount,
            HardwareWalletAddressJson changeAddress,
            HardwareWalletAddressJson nextAddress
    ) {

        this.sortedUtxos = sortedUtxos;
        this.sizeForAmount = sizeForAmount;
        this.changeAddress = changeAddress;
        this.nextAddress = nextAddress;
    }

    /**
     * Json constructor.
     */
    public HardwareWalletStateJson() {

    }
}
