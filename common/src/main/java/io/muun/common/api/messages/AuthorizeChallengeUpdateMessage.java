package io.muun.common.api.messages;

import io.muun.common.api.PendingChallengeUpdateJson;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizeChallengeUpdateMessage extends AbstractMessage {

    public static final String TYPE = "challenge/update/authorize";
    public static final SessionStatus PERMISSION = SessionStatus.LOGGED_IN;

    public PendingChallengeUpdateJson pendingUpdateJson;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public SessionStatus getPermission() {
        return PERMISSION;
    }

    /**
     * Json constructor.
     */
    public AuthorizeChallengeUpdateMessage() {
    }

    /**
     * Constructor.
     */
    public AuthorizeChallengeUpdateMessage(PendingChallengeUpdateJson pendingUpdateJson) {
        this.pendingUpdateJson = pendingUpdateJson;
    }
}
