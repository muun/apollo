package io.muun.common.api.messages;

import io.muun.common.model.SessionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventCommunicationMessage extends AbstractMessage {

    public enum Event {
        TAPROOT_PREACTIVATION,
        TAPROOT_ACTIVATED
    }

    public static final MessageSpec SPEC = new MessageSpec(
            "event_communication",
            SessionStatus.LOGGED_IN,
            MessageOrigin.HOUSTON
    );

    public Event event;

    @Override
    public String toLog() {
        return String.format("event %s", event.toString());
    }

    /**
     * Json constructor.
     */
    public EventCommunicationMessage() {
    }

    /**
     * Houston constructor.
     */
    public EventCommunicationMessage(final Event event) {
        this.event = event;
    }

    @Override
    public MessageSpec getSpec() {
        return SPEC;
    }
}
