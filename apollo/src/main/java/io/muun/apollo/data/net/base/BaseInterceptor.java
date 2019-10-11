package io.muun.apollo.data.net.base;

import androidx.annotation.NonNull;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class BaseInterceptor implements Interceptor {

    protected Request processRequest(Request originalRequest) {
        return originalRequest;
    }

    protected Response processResponse(Response originalResponse) {
        return originalResponse;
    }

    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        final Request request = processRequest(chain.request());
        final Response response = processResponse(chain.proceed(request));

        return response;
    }
}
