package io.muun.common.api.beam.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationJson {

    @NotNull
    public Long id; // this is the idInChannel

    @NotNull
    public Long previousId;

    @NotNull
    public String uuid;

    @NotEmpty
    public String senderSessionUuid;

    @NotNull
    public String messageType;

    @NotNull
    public Object message;

    @Nullable
    public String inReplyTo;

    /**
     * Json constructor.
     */
    public NotificationJson() {
    }

    /**
     * Beam constructor.
     */
    public NotificationJson(
            Long id,
            @Nullable Long previousId,
            String uuid,
            String senderSessionUuid,
            String messageType,
            Object message,
            @Nullable String inReplyTo) {

        this.id = id;
        this.previousId = previousId;
        this.uuid = uuid;
        this.senderSessionUuid = senderSessionUuid;
        this.messageType = messageType;
        this.message = message;
        this.inReplyTo = inReplyTo;
    }
}
