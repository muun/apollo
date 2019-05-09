package io.muun.apollo.domain.errors;

import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.SessionStatus;

public class MessagePermissionsError extends RuntimeException {

    /**
     * Rich constructor.
     */
    public MessagePermissionsError(String sessionUuid,
                                   long messageId,
                                   SessionStatus currentStatus,
                                   MessageSpec spec) {

        super(String.format(
                "Sender %s sent message %s of type %s expecting %s, but session was %s",
                sessionUuid,
                Long.toString(messageId),
                spec.messageType,
                spec.permission.name(),
                currentStatus != null ? currentStatus.name() : "null"
        ));
    }
}
