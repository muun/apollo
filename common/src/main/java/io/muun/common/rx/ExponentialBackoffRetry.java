package io.muun.common.rx;

import io.muun.common.Optional;
import io.muun.common.utils.MathUtils;

import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;


public class ExponentialBackoffRetry implements
        Func1<Observable<? extends Throwable>, Observable<?>> {

    private final long baseInterval;
    private final TimeUnit timeUnit;
    private final int maxRetries;
    private final Class<? extends Throwable> retryErrorType;

    private final ShouldRetry shouldRetryGivenDelay;

    private Throwable lastError;

    private static class CallState {
        int retryCount = 0;
    }

    /**
     * A Retry strategy that waits an exponentially increasing amount of time before each attempt.
     *
     * @param baseInterval   the initial delay magnitude
     * @param timeUnit       the time unit for inital delay magnitude
     * @param maxRetries     the maximum amount of retries before failing
     * @param retryErrorType the error type that will trigger a retry
     */
    public ExponentialBackoffRetry(
            long baseInterval,
            TimeUnit timeUnit,
            int maxRetries,
            Class<? extends Throwable> retryErrorType
    ) {
        this(baseInterval, timeUnit, maxRetries, retryErrorType, (_l, _u) -> true);
    }

    /**
     * A Retry strategy that waits an exponentially increasing amount of time before each attempt.
     *
     * @param baseInterval          the initial delay magnitude
     * @param timeUnit              the time unit for inital delay magnitude
     * @param maxRetries            the maximum amount of retries before failing
     * @param retryErrorType        the error type that will trigger a retry
     * @param shouldRetryGivenDelay the hook to decide whether a given retry would take too long
     */
    public ExponentialBackoffRetry(
            long baseInterval,
            TimeUnit timeUnit,
            int maxRetries,
            Class<? extends Throwable> retryErrorType,
            ShouldRetry shouldRetryGivenDelay
    ) {
        this.baseInterval = baseInterval;
        this.timeUnit = timeUnit;
        this.maxRetries = maxRetries;
        this.retryErrorType = retryErrorType;
        this.shouldRetryGivenDelay = shouldRetryGivenDelay;
    }

    @Override
    public Observable<?> call(Observable<? extends Throwable> errors) {
        final CallState state = new CallState();

        return errors.flatMap((Func1<Throwable, Observable<?>>) error -> {

            lastError = error;

            final long delayForRetry = getDelayForRetry(state.retryCount + 1);

            if (state.retryCount < maxRetries
                    && shouldRetry(error)
                    && shouldRetryGivenDelay.shouldRetry(delayForRetry, timeUnit)
            ) {
                state.retryCount++;
                return Observable.timer(
                        delayForRetry,
                        timeUnit,
                        Schedulers.trampoline()
                );

            } else {
                return Observable.error(error);
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

    public Optional<Throwable> getLastError() {
        return Optional.ofNullable(lastError);
    }

    public interface ShouldRetry {

        /**
         * Decide whether a given retry should happen given its delay.
         */
        boolean shouldRetry(final long delay, final TimeUnit unit);
    }
}
