package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailVerifiedMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "users/email_verified",
            SessionStatus.LOGGED_IN,
            MessageOrigin.HOUSTON
    );

    /**
     * constructor.
     */
    public EmailVerifiedMessage() {
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
