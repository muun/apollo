package io.muun.common.api.beam.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationRequestJson {

    @Null
    @Nullable
    public String uuid;

    @NotNull
    public NotificationPriorityJson priority;

    @Null
    @Nullable
    public NotificationStatusJson status;

    @NotNull
    public String messageType;

    @NotNull
    public Object message;

    @Nullable
    public String inReplyTo;

    /**
     * Json constructor.
     */
    public NotificationRequestJson() {
    }

    /**
     * Client constructor.
     */
    public NotificationRequestJson(
            NotificationPriorityJson priority,
            String messageType,
            Object message,
            @Nullable String inReplyTo) {

        this.priority = priority;
        this.messageType = messageType;
        this.message = message;
        this.inReplyTo = inReplyTo;
    }

    /**
     * Beam constructor.
     */
    public NotificationRequestJson(
            String uuid,
            NotificationPriorityJson priority,
            NotificationStatusJson status,
            String messageType,
            Object message,
            @Nullable String inReplyTo) {

        this.uuid = uuid;
        this.priority = priority;
        this.status = status;
        this.messageType = messageType;
        this.message = message;
        this.inReplyTo = inReplyTo;
    }
}
