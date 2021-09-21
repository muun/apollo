package io.muun.apollo.data.net.base.interceptor;

import io.muun.apollo.data.net.base.BaseInterceptor;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.common.net.HeaderUtils;

import android.content.Context;
import okhttp3.Request;

import javax.inject.Inject;

public class LanguageHeaderInterceptor extends BaseInterceptor {

    private final Context applicationContext;

    @Inject
    public LanguageHeaderInterceptor(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    protected Request processRequest(Request request) {

        final String lang = ExtensionsKt.locale(applicationContext).getLanguage();

        return request.newBuilder()
                .addHeader(
                        HeaderUtils.CLIENT_LANGUAGE,
                        !lang.isEmpty() ? lang : HeaderUtils.DEFAULT_LANGUAGE_VALUE
                )
                .build();
    }
}
