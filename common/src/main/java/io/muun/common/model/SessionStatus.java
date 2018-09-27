package io.muun.common.model;

/**
 * The possible flows of the session state are the following.
 *
 * <pre>
 *
 * - the sign up flow
 *
 *   CREATED -> LOGGED_IN -> EXPIRED
 *
 * - the log in flow
 *
 *   CREATED -> BLOCKED_BY_EMAIL -> AUTHORIZED_BY_EMAIL -> LOGGED_IN -> EXPIRED
 *
 * </pre>
 */
public enum SessionStatus {

    /**
     * The session has been created with an e-mail, and has a Beam channel.
     */
    CREATED,

    /**
     * The session requires clicking an email link sent by Houston before proceeding.
     */
    BLOCKED_BY_EMAIL,

    /**
     * The session has been cleared by Houston after the email link was clicked.
     */
    AUTHORIZED_BY_EMAIL,

    /**
     * The session has a User attached and is ready to use all available endpoints.
     */
    LOGGED_IN,

    /**
     * The session has been expired.
     */
    EXPIRED;

    public boolean hasPermisionFor(SessionStatus requiredStatus) {
        return this != EXPIRED && this.compareTo(requiredStatus) >= 0;
    }
}
