package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailVerifiedMessage extends AbstractMessage {

    public static final String TYPE = "users/email_verified";

    public static final SessionStatus PERMISSION = SessionStatus.LOGGED_IN;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override public SessionStatus getPermission() {
        return PERMISSION;
    }

    /**
     * constructor.
     */
    public EmailVerifiedMessage() {
    }
}
