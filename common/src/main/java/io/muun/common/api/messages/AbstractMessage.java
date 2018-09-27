package io.muun.common.api.messages;

public abstract class AbstractMessage implements Message {

    public String toLog() {
        return getType();
    }
}
