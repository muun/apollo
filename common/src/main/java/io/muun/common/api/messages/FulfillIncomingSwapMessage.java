package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FulfillIncomingSwapMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "incoming_swap/fulfill",
            SessionStatus.LOGGED_IN,
            MessageOrigin.HOUSTON
    );

    public String uuid;

    @Override
    public String toLog() {
        return String.format("Requesting fulfillment of incoming swap %s", uuid);
    }

    /**
     * Json constructor.
     */
    public FulfillIncomingSwapMessage() {
    }

    /**
     * Houston constructor.
     */
    public FulfillIncomingSwapMessage(final String uuid) {
        this.uuid = uuid;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
