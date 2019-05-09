package io.muun.apollo.domain.satellite.messages;

import io.muun.common.api.messages.AbstractMessage;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WithdrawalResultMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "satellite/withdrawalResult",
            SessionStatus.LOGGED_IN,
            MessageOrigin.SATELLITE
    );

    @NotNull
    public String uuid;
    
    @Nullable
    public String signedTransaction;

    @Nullable
    public Integer errorCode;

    /**
     * Json constructor.
     */
    public WithdrawalResultMessage() {
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
