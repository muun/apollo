package io.muun.common.model;

public enum OperationDirection {

    /**
     * This Operation was sent to the User.
     */
    INCOMING,

    /**
     * This Operation was sent by the User.
     */
    OUTGOING,

    /**
     * This Operation was to the User, by the same User.
     */
    CYCLICAL

}
