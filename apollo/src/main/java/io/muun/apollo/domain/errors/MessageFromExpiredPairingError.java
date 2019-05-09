package io.muun.apollo.domain.errors;

import io.muun.common.api.messages.MessageSpec;

public class MessageFromExpiredPairingError extends RuntimeException {

    /**
     * Rich constructor.
     */
    public MessageFromExpiredPairingError(String sessionUuid, long messageId, MessageSpec spec) {
        super(String.format(
                "Sender %s sent message %s of type %s, but the pairing was expired",
                sessionUuid,
                Long.toString(messageId),
                spec.messageType
        ));
    }
}
