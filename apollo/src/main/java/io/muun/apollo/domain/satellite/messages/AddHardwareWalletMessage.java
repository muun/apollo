package io.muun.apollo.domain.satellite.messages;

import io.muun.common.api.messages.AbstractMessage;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.HardwareWalletBrand;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddHardwareWalletMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "satellite/addHardwareWallet",
            SessionStatus.LOGGED_IN,
            MessageOrigin.SATELLITE
    );

    @NotNull
    public HardwareWalletBrand brand;
    
    @NotNull
    public String model;

    @NotNull
    public String label;

    @NotNull
    public String basePublicKey;

    @NotNull
    public String basePublicKeyPath;

    /**
     * Json constructor.
     */
    public AddHardwareWalletMessage() {
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
