package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationJson {

    @NotNull
    public Long id;

    @NotNull
    public Long previousId;

    @NotEmpty
    public String senderSessionUuid;

    @NotNull
    public String messageType;

    @NotNull
    public Object message;

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
            String senderSessionUuid,
            String messageType,
            Object message) {

        this.id = id;
        this.previousId = previousId;
        this.senderSessionUuid = senderSessionUuid;
        this.messageType = messageType;
        this.message = message;
    }
}
