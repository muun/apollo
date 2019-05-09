package io.muun.apollo.domain.satellite.messages;

import io.muun.common.api.messages.AbstractMessage;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionTakeoverMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "session/takeover",
            SessionStatus.LOGGED_IN,
            MessageOrigin.ANY
    );

    /**
     * Json constructor.
     */
    public SessionTakeoverMessage() {
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
