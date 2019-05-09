package io.muun.apollo.domain.errors;

import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;

public class MessageOriginError extends RuntimeException {

    /**
     * Rich constructor.
     */
    public MessageOriginError(String sessionUuid,
                              long messageId,
                              MessageOrigin origin,
                              MessageSpec spec) {

        super(String.format(
                "Sender %s sent message %s of type %s from %s, but we expected %s",
                sessionUuid,
                Long.toString(messageId),
                spec.messageType,
                origin.name(),
                spec.allowedOrigin.name()
        ));
    }
}
