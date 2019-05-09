package io.muun.common.api.messages;

import io.muun.common.api.PendingChallengeUpdateJson;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizeChallengeUpdateMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "challenge/update/authorize",
            SessionStatus.LOGGED_IN,
            MessageOrigin.HOUSTON
    );

    public PendingChallengeUpdateJson pendingUpdateJson;

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

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
