package io.muun.apollo.data.net.base;

import io.muun.common.net.NetworkRetry;
import io.muun.common.net.ServerRetry;
import io.muun.common.rx.ExponentialBackoffRetry;
import io.muun.common.rx.ObservableFn;

import rx.Observable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HttpRetryTransformer<T> implements Observable.Transformer<T, T> {

    // TODO move this to external configuration:
    private static final long TIMEOUT_SECONDS = 60;

    private final NetworkRetry networkRetryConfig;
    private final ServerRetry serverRetryConfig;

    public HttpRetryTransformer(NetworkRetry networkRetryConfig, ServerRetry serverRetryConfig) {
        this.networkRetryConfig = networkRetryConfig;
        this.serverRetryConfig = serverRetryConfig;
    }

    @Override
    public Observable<T> call(Observable<T> observable) {

        final ExponentialBackoffRetry networkExceptionRetryPolicy = buildNetworkRetryPolicy();
        final ExponentialBackoffRetry serverExceptionRetryPolicy = buildServerRetryPolicy();

        final Observable.Transformer<T, T> timeoutTransformer = ObservableFn.replaceTypedError(
                TimeoutException.class,
                timeoutException -> networkExceptionRetryPolicy.getLastError()
                        .ifEmptyGet(serverExceptionRetryPolicy::getLastError)
                        .orElse(new NetworkException(timeoutException))
        );

        return observable
                .retryWhen(networkExceptionRetryPolicy)
                .retryWhen(serverExceptionRetryPolicy)
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .compose(timeoutTransformer);
    }

    private ExponentialBackoffRetry buildNetworkRetryPolicy() {

        if (networkRetryConfig != null) {
            return new ExponentialBackoffRetry(
                    networkRetryConfig.baseIntervalInMs(),
                    TimeUnit.MILLISECONDS,
                    networkRetryConfig.count(),
                    NetworkException.class
            );
        }

        return new ExponentialBackoffRetry(1, TimeUnit.SECONDS, 7, NetworkException.class);
    }

    private ExponentialBackoffRetry buildServerRetryPolicy() {

        if (serverRetryConfig != null) {
            return new ExponentialBackoffRetry(
                    serverRetryConfig.baseIntervalInMs(),
                    TimeUnit.MILLISECONDS,
                    serverRetryConfig.count(),
                    ServerFailureException.class
            );
        }

        return new ExponentialBackoffRetry(2, TimeUnit.SECONDS, 3, ServerFailureException.class);
    }
}
