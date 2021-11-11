package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoOpMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "no-op",
            SessionStatus.CREATED,
            MessageOrigin.HOUSTON
    );

    public NoOpMessage() {
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
