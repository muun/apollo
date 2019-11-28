package io.muun.apollo.data.net.base.interceptor;

import io.muun.apollo.data.logging.LoggingRequestTracker;
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
            // TODO find out why, when how, can this hapen
            return originalRequest;
        }

        reportRequest(idempotencyKey, originalRequest.url().toString());

        return originalRequest.newBuilder()
                .addHeader(HeaderUtils.IDEMPOTENCY_KEY, idempotencyKey)
                .build();
    }

    @Override
    protected Response processResponse(Response originalResponse) {
        final Request req = originalResponse.request();
        final String idempotencyKey = req.headers().get(HeaderUtils.IDEMPOTENCY_KEY);

        if (idempotencyKey != null) {
            // TODO find out why, when how, can this happen
            // Under some weird UNKNOWN circumstances, idempotencyKey CAN actually be null
            reportResponse(idempotencyKey, originalResponse.code());
        }

        return originalResponse;
    }

    private void reportResponse(String idempotencyKey, int resCode) {
        LoggingRequestTracker.INSTANCE.reportRecentResponse(idempotencyKey, resCode);
    }

    private void reportRequest(String idempotencyKey, String url) {
        LoggingRequestTracker.INSTANCE.reportRecentRequest(idempotencyKey, url);
    }
}
