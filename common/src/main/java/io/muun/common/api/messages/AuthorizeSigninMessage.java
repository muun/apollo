package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizeSigninMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "sessions/authorized",
            SessionStatus.BLOCKED_BY_EMAIL,
            MessageOrigin.HOUSTON
    );

    /**
     * constructor.
     */
    public AuthorizeSigninMessage() {
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
