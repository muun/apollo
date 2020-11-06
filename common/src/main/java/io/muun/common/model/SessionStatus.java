package io.muun.common.model;

import io.muun.common.exception.MissingCaseError;

import com.google.common.collect.Lists;

import java.util.List;

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
 * - the log in by inputting recovery code first flow (introduced during email-less recovery)
 *
 *   1. Without email set up
 *   CREATED -> BLOCKED_BY_RC -> LOGGED_IN -> EXPIRED
 *
 *   2. With email set up
 *   CREATED -> BLOCKED_BY_RC -> BLOCKED_BY_EMAIL -> LOGGED_IN -> EXPIRED
 *
 * </pre>
 */
public enum SessionStatus {

    /**
     * The session requires signing the recovery code challenge.
     */
    BLOCKED_BY_RC,

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

    /**
     * This method is not pretty, but since the states are not lineal anymore, we need to determine
     * the permissions individually.
     * The way to think about this method is:
     * - Does current (`this`) session status has permission for `requiredStatus` ?
     */
    public boolean hasPermissionFor(SessionStatus requiredStatus) {

        switch (this) {
            case BLOCKED_BY_RC:
                final List<SessionStatus> blockedByRcStates = Lists.newArrayList(
                        CREATED, BLOCKED_BY_RC
                );
                return blockedByRcStates.contains(requiredStatus);

            case CREATED:
                return requiredStatus == CREATED;

            case BLOCKED_BY_EMAIL:
                final List<SessionStatus> blockedByEmailStates = Lists.newArrayList(
                        CREATED, BLOCKED_BY_RC, BLOCKED_BY_EMAIL
                );
                return blockedByEmailStates.contains(requiredStatus);

            case AUTHORIZED_BY_EMAIL:
                final List<SessionStatus> authorizedByEmailStates = Lists.newArrayList(
                        CREATED, BLOCKED_BY_EMAIL, AUTHORIZED_BY_EMAIL
                );
                return authorizedByEmailStates.contains(requiredStatus);

            case LOGGED_IN:
                final List<SessionStatus> loggedInStates = Lists.newArrayList(
                        CREATED, BLOCKED_BY_RC, BLOCKED_BY_EMAIL, AUTHORIZED_BY_EMAIL, LOGGED_IN
                );
                return loggedInStates.contains(requiredStatus);

            case EXPIRED:
                return false;

            default:
                throw new MissingCaseError(this); // This should never happen
        }

    }

}
