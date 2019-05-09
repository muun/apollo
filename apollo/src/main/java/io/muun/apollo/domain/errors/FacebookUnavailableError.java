package io.muun.apollo.domain.errors;

@Deprecated
public class FacebookUnavailableError extends RuntimeException {

    public FacebookUnavailableError() {
        super("We couldn't look at your Facebook profile. Please, try again later.");
    }
}
