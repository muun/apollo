package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

public class MessageSpec {

    /**
     * The protocol-level `messageType` this object specifies.
     */
    public final String messageType;

    /**
     * The minimum required SessionStatus to process this message.
     */
    public final SessionStatus permission;

    /**
     * The origin restrictions to verify before processing this message.
     */
    public final MessageOrigin allowedOrigin;

    /**
     * Constructor.
     */
    public MessageSpec(String messageType,
                       SessionStatus permission,
                       MessageOrigin allowedOrigin) {

        this.messageType = messageType;
        this.permission = permission;
        this.allowedOrigin = allowedOrigin;
    }
}
