package io.muun.common.net;

import io.muun.common.Optional;
import io.muun.common.api.ClientTypeJson;
import io.muun.common.model.SessionStatus;

import javax.annotation.Nullable;

public class HeaderUtils {

    public static final String AUTHORIZATION = "Authorization";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CLIENT_VERSION = "X-Client-Version";
    public static final String CLIENT_LANGUAGE = "X-Client-Language";
    public static final String CLIENT_TYPE = "X-Client-Type";
    public static final String CLIENT_SDK_VERSION = "X-Client-Sdk-Version";
    public static final String MIN_CLIENT_VERSION = "X-Min-Client-Version";
    public static final String FORWARDED_FOR = "X-Forwarded-For";
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
    public static final String SESSION_STATUS = "X-Session-Status";
    public static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String DEFAULT_LANGUAGE_VALUE = "en";

    /**
     * Returns a token from a "Bearer token" header, or empty if malformed or absent.
     */
    public static Optional<String> getBearerTokenFromHeader(@Nullable String headerValue) {

        if (headerValue == null) {
            return Optional.empty();
        }

        final String[] parts = headerValue.split(" ");

        if (parts.length != 2 || !parts[0].equalsIgnoreCase("bearer")) {
            return Optional.empty();
        }

        return Optional.of(parts[1]);
    }

    /**
     * Returns a version number from a numerical header, or empty if malformed or absent.
     */
    public static Optional<Integer> getMinVersionFromHeader(@Nullable String headerValue) {
        try {
            return Optional.of(Integer.parseInt(headerValue));

        } catch (NumberFormatException ex) {
            // Integer.parseInt() fails on non-numerical input and `null` (if header is absent)
            return Optional.empty();
        }
    }

    /**
     * Returns the current session status from a "session-status" header, or empty if absent.
     */
    public static Optional<SessionStatus> getSessionStatusFromHeader(@Nullable String headerValue) {

        if (headerValue == null) {
            return Optional.empty();
        }

        return Optional.of(SessionStatus.valueOf(headerValue));
    }

    /**
     * Returns the client type from a header or empty if malformed.
     */
    public static Optional<ClientTypeJson> getClientTypeFromHeader(@Nullable String headerValue) {

        if (headerValue == null) {
            return Optional.of(ClientTypeJson.APOLLO);
        }

        try {
            return Optional.of(ClientTypeJson.valueOf(headerValue));

        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
