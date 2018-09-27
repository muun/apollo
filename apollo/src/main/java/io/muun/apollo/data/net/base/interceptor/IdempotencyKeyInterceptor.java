package io.muun.apollo.data.net.base.interceptor;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.base.BaseInterceptor;
import io.muun.apollo.data.net.base.CallAdapterFactory;
import io.muun.common.net.HeaderUtils;

import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;

public class IdempotencyKeyInterceptor extends BaseInterceptor {

    private final CallAdapterFactory provider;

    @Inject
    public IdempotencyKeyInterceptor(CallAdapterFactory provider) {
        this.provider = provider;
    }

    @Override
    protected Request processRequest(Request originalRequest) {

        final String idempotencyKey = provider.getIdempotencyKey();

        if (idempotencyKey == null) {
            return originalRequest;
        }

        Logger.saveRequestUri(idempotencyKey, originalRequest.url().toString());

        return originalRequest.newBuilder()
                .addHeader(HeaderUtils.IDEMPOTENCY_KEY, idempotencyKey)
                .build();
    }

    @Override
    protected Response processResponse(Response originalResponse) {
        final Request request = originalResponse.request();
        final String idempotencyKey = request.headers().get(HeaderUtils.IDEMPOTENCY_KEY);

        Logger.saveRequestUri(idempotencyKey, request.url().toString() + originalResponse.code());

        return originalResponse;
    }
}
