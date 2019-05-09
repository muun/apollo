package io.muun.common.api.messages;

import io.muun.common.api.Contact;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewContactMessage extends AbstractMessage {

    public static final MessageSpec SPEC = new MessageSpec(
            "contact/new",
            SessionStatus.LOGGED_IN,
            MessageOrigin.HOUSTON
    );

    public Contact contact;

    /**
     * Json constructor.
     */
    public NewContactMessage() {
    }

    /**
     * Houston constructor.
     */
    public NewContactMessage(Contact contact) {
        this.contact = contact;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
