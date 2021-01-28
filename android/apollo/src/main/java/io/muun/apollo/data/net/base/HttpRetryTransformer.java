package io.muun.apollo.data.net.base;

import io.muun.common.rx.ExponentialBackoffRetry;
import io.muun.common.rx.ObservableFn;

import rx.Observable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HttpRetryTransformer<T> implements Observable.Transformer<T, T> {

    private static final long TIMEOUT_SECONDS = 60;

    @Override
    public Observable<T> call(Observable<T> observable) {

        final ExponentialBackoffRetry networkExceptionRetryPolicy =
                new ExponentialBackoffRetry(1, 7, NetworkException.class);

        final ExponentialBackoffRetry serverExceptionRetryPolicy =
                new ExponentialBackoffRetry(2, 3, ServerFailureException.class);

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
}
