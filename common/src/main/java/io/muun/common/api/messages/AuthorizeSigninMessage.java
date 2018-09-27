package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizeSigninMessage extends AbstractMessage {

    public static final String TYPE = "sessions/authorized";
    public static final SessionStatus PERMISSION = SessionStatus.BLOCKED_BY_EMAIL;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public SessionStatus getPermission() {
        return PERMISSION;
    }

    /**
     * constructor.
     */
    public AuthorizeSigninMessage() {
    }
}
