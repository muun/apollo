package io.muun.common.rx;

import io.muun.common.utils.MathUtils;

import rx.Observable;
import rx.functions.Func1;

import java.util.concurrent.TimeUnit;


public class ExponentialBackoffRetry implements
        Func1<Observable<? extends Throwable>, Observable<?>> {

    private final long baseInterval;
    private final int maxRetries;
    private final Class<? extends Throwable> retryErrorType;

    private static class CallState {
        int retryCount = 0;
    }

    public ExponentialBackoffRetry(long baseInterval, int maxRetries) {
        this(baseInterval, maxRetries, Throwable.class);
    }

    /**
     * A Retry strategy that waits an exponentially increasing amount of time before each attempt.
     *
     * @param baseInterval the initial delay magnitude
     * @param maxRetries the maximum amount of retries before failing
     * @param retryErrorType the error type that will trigger a retry
     */
    public ExponentialBackoffRetry(
            long baseInterval,
            int maxRetries,
            Class<? extends Throwable> retryErrorType) {

        this.baseInterval = baseInterval;
        this.maxRetries = maxRetries;
        this.retryErrorType = retryErrorType;
    }

    @Override
    public Observable<?> call(Observable<? extends Throwable> errors) {
        final CallState state = new CallState();

        return errors.flatMap(new Func1<Throwable, Observable<?>>() {

            public Observable<?> call(Throwable error) {
                if (state.retryCount < maxRetries && shouldRetry(error)) {
                    state.retryCount++;
                    return Observable.timer(getDelayForRetry(state.retryCount), TimeUnit.SECONDS);

                } else {
                    return Observable.error(error);
                }
            }

        });
    }

    /**
     * Subclasses can override this method to decide whether this strategy should retry after a
     * specific error, or abort the sequence. By default, all errors of type {retryErrorType} are
     * retried.
     */
    protected boolean shouldRetry(Throwable error) {
        return retryErrorType.isInstance(error);
    }

    private long getDelayForRetry(int retryNumber) {
        return MathUtils.longPow(2, retryNumber - 1) * baseInterval;
    }
}
