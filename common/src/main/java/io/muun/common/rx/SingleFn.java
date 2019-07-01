package io.muun.common.rx;

import io.muun.common.Optional;
import io.muun.common.api.error.ErrorCode;
import io.muun.common.exception.HttpException;
import io.muun.common.utils.ExceptionUtils;

import rx.Single;
import rx.functions.Func1;

/**
 * Utility transformers for modifying singles.
 */
public final class SingleFn {

    private SingleFn() {
        throw new AssertionError();
    }

    /**
     * Composition transformer with the same functionality as a doOnSuccess, that accepts a function
     * that returns an single. The entire single will be consumed before proceeding with the
     * execution.
     */
    public static <T, U> Single.Transformer<T, T> flatDoOnSuccess(final Func1<T, Single<U>> func) {

        return single -> single.flatMap(
                returnedItem -> {
                    return func.call(returnedItem).map(
                            ignoredItem -> returnedItem
                    );
                }
        );
    }

    /**
     * If the error emitted by the single is of type {ErrorT}, it gets replaced by the error
     * returned when calling replacer with the original error.
     */
    public static <T, ErrorT extends Throwable> Single.Transformer<T, T> replaceTypedError(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Throwable> replacer) {

        return onTypedErrorResumeNext(
                errorClass,
                error -> Single.error(replacer.call(error))
        );
    }

    /**
     * Like onErrorResumeNext, but only for errors of a specific type.
     */
    public static <T, ErrorT extends Throwable> Single.Transformer<T, T> onTypedErrorResumeNext(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Single<T>> resumeFunction) {

        return single -> single.onErrorResumeNext(

                (Func1<Throwable, Single<? extends T>>) error -> {

                    final Optional<ErrorT> cause = ExceptionUtils.getTypedCause(error, errorClass);

                    if (cause.isPresent()) {
                        return resumeFunction.call(errorClass.cast(cause.get()));

                    } else {
                        return Single.error(error);
                    }

                }
        );
    }

    /**
     * If the error emitted by the single is of type HttpException and has a specific code, it gets
     * replaced by the error returned when calling replacer with the original error.
     */
    public static <T> Single.Transformer<T, T> replaceHttpException(
            final ErrorCode code,
            final Func1<HttpException, Throwable> replacer) {

        return onHttpExceptionResumeNext(
                code,
                error -> Single.error(replacer.call(error))
        );
    }

    /**
     * Like onTypedErrorResumeNext, but specialized to HttpExceptions.
     */
    public static <T> Single.Transformer<T, T> onHttpExceptionResumeNext(
            final ErrorCode code,
            final Func1<HttpException, Single<T>> resumeFunction) {

        return single -> single.onErrorResumeNext(

                (Func1<Throwable, Single<? extends T>>) error -> {

                    final Optional<HttpException> cause = ExceptionUtils.getTypedCause(
                            error,
                            HttpException.class
                    );

                    if (cause.isPresent() && code.equals(cause.get().getErrorCode())) {
                        return resumeFunction.call(cause.get());

                    } else {
                        return Single.error(error);
                    }

                }
        );
    }
}
