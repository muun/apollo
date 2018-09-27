package io.muun.apollo.data.net.base.interceptor;


import io.muun.apollo.BuildConfig;
import io.muun.apollo.data.net.base.BaseInterceptor;
import io.muun.apollo.data.preferences.ClientVersionRepository;
import io.muun.common.net.HeaderUtils;

import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;

public class VersionHeaderInterceptor extends BaseInterceptor {

    private final ClientVersionRepository clientVersionRepository;

    @Inject
    public VersionHeaderInterceptor(ClientVersionRepository clientVersionRepository) {
        this.clientVersionRepository = clientVersionRepository;
    }

    @Override
    protected Request processRequest(Request originalRequest) {
        return originalRequest.newBuilder()
                .addHeader(HeaderUtils.CLIENT_VERSION, "" + BuildConfig.VERSION_CODE)
                .addHeader(HeaderUtils.CLIENT_SDK_VERSION,
                        "" + android.os.Build.VERSION.SDK_INT)
                .build();
    }

    @Override
    protected Response processResponse(Response originalResponse) {
        final String versionHeader = originalResponse.header(HeaderUtils.MIN_CLIENT_VERSION);

        HeaderUtils.getMinVersionFromHeader(versionHeader)
                .ifPresent(clientVersionRepository::storeMinClientVersion);

        return super.processResponse(originalResponse);
    }
}
