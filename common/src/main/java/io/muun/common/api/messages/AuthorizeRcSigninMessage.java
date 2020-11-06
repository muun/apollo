package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizeRcSigninMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "sessions/rc-authorized",
            SessionStatus.BLOCKED_BY_EMAIL,
            MessageOrigin.HOUSTON
    );

    /**
     * Constructor.
     */
    public AuthorizeRcSigninMessage() {
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
