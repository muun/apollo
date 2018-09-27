package io.muun.common.api.messages;

import io.muun.common.api.Contact;
import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactUpdateMessage extends AbstractMessage {

    public static final String TYPE = "contact/update";

    public static final SessionStatus PERMISSION = SessionStatus.LOGGED_IN;

    public Contact contact;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override public SessionStatus getPermission() {
        return PERMISSION;
    }

    /**
     * Json constructor.
     */
    public ContactUpdateMessage() {
    }

    /**
     * Houston constructor.
     */
    public ContactUpdateMessage(Contact contact) {
        this.contact = contact;
    }
}
