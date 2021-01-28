package io.muun.apollo.data.net.base.interceptor;

import io.muun.apollo.data.net.base.BaseInterceptor;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.common.net.HeaderUtils;

import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;

public class AuthHeaderInterceptor extends BaseInterceptor {

    private final AuthRepository authRepository;

    @Inject
    public AuthHeaderInterceptor(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @Override
    protected Request processRequest(Request request) {
        // Attach the request header if a token is available:

        final String serverJwt = authRepository.getServerJwt().orElse(null);

        if (serverJwt != null) {
            return request.newBuilder()
                    .addHeader(HeaderUtils.AUTHORIZATION, "Bearer " + serverJwt)
                    .build();

        } else {
            return request;
        }
    }

    @Override
    protected Response processResponse(Response response) {
        // Save the token in the response header if one is found:
        final String authHeaderValue = response.header(HeaderUtils.AUTHORIZATION);

        HeaderUtils.getBearerTokenFromHeader(authHeaderValue)
                .ifPresent(authRepository::storeServerJwt);

        // We need a reliable way (across all envs: local, CI, prd, etc...) to identify the logout
        // requests. We could inject HoustonConfig and build the entire URL (minus port)
        // or... we can do this :)
        final String url = response.request().url().url().toString();
        final boolean isLogout = url.endsWith("sessions/logout");

        if (!isLogout) {
            final String sessionStatusHeaderValue = response.header(HeaderUtils.SESSION_STATUS);

            HeaderUtils.getSessionStatusFromHeader(sessionStatusHeaderValue)
                    .ifPresent(authRepository::storeSessionStatus);
        }

        return response;
    }
}
