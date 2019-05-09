package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpiredSessionMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "session/expired",
            SessionStatus.LOGGED_IN,
            MessageOrigin.ANY
    );

    public String expiredSessionUuid;

    /**
     * Json constructor.
     */
    public ExpiredSessionMessage() {
    }

    /**
     * Apollo constructor.
     */
    public ExpiredSessionMessage(String expiredSessionUuid) {
        this.expiredSessionUuid = expiredSessionUuid;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
