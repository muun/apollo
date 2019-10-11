package io.muun.apollo.data.net.base.interceptor;

import io.muun.apollo.data.logging.Logger;
import io.muun.common.utils.Encodings;

import androidx.annotation.NonNull;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;


public class DuplicatingInterceptor implements Interceptor {

    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {

        final Response firstResponse = chain.proceed(chain.request());
        final Response secondResponse = chain.proceed(chain.request());

        final ResponseBody firstBody = firstResponse.body();
        final ResponseBody secondBody = secondResponse.body();

        final String firstString = firstBody != null ? firstBody.string() : "";
        final String secondString = secondBody != null ? secondBody.string() : "";

        if (!firstString.equals(secondString)) {
            Logger.error(
                    "Responses don't match\n\nFirst response:\n%s\n\nSecond response:\n%s",
                    firstString,
                    secondString
            );
        }

        return firstResponse.newBuilder().body(
                ResponseBody.create(
                        firstBody != null ? firstBody.contentType() : null,
                        Encodings.stringToBytes(firstString)
                )
        ).build();
    }
}
