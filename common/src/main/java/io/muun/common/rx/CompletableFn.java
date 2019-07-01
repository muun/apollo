package io.muun.common.rx;

import io.muun.common.Optional;
import io.muun.common.api.error.ErrorCode;
import io.muun.common.exception.HttpException;
import io.muun.common.utils.ExceptionUtils;

import rx.Completable;
import rx.functions.Func1;

/**
 * Utility transformers for modifying completables.
 */
public final class CompletableFn {

    private CompletableFn() {
        throw new AssertionError();
    }

    /**
     * If the error emitted by the completable is of type {ErrorT}, it gets replaced by the error
     * returned when calling replacer with the original error.
     */
    public static <ErrorT extends Throwable> Completable.Transformer replaceTypedError(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Throwable> replacer) {

        return onTypedErrorResumeNext(
                errorClass,
                error -> Completable.error(replacer.call(error))
        );
    }

    /**
     * Consume and ignore errors of a given type.
     */
    public static <ErrorT extends Throwable> Completable.Transformer ignoreTypedError(
            final Class<ErrorT> errorClass) {

        return onTypedErrorResumeNext(
                errorClass,
                error -> Completable.complete()
        );
    }

    /**
     * Like onErrorResumeNext, but only for errors of a specific type.
     */
    public static <ErrorT extends Throwable> Completable.Transformer onTypedErrorResumeNext(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Completable> resumeFunction) {

        return completable -> completable.onErrorResumeNext(
                error -> {

                    final Optional<ErrorT> cause = ExceptionUtils.getTypedCause(error, errorClass);

                    if (cause.isPresent()) {
                        return resumeFunction.call(errorClass.cast(cause.get()));

                    } else {
                        return Completable.error(error);
                    }
                }
        );
    }

    /**
     * Consume and ignore HttpExceptions of a given type.
     */
    public static Completable.Transformer ignoreHttpException(final ErrorCode code) {

        return onHttpExceptionResumeNext(
                code,
                error -> Completable.complete()
        );
    }

    /**
     * Like onTypedErrorResumeNext, but specialized to HttpExceptions.
     */
    public static Completable.Transformer onHttpExceptionResumeNext(
            final ErrorCode code,
            final Func1<HttpException, Completable> resumeFunction) {

        return completable -> completable.onErrorResumeNext(
                error -> {
                    final Optional<HttpException> cause = ExceptionUtils.getTypedCause(
                            error,
                            HttpException.class
                    );

                    if (cause.isPresent() && code.equals(cause.get().getErrorCode())) {
                        return resumeFunction.call(cause.get());

                    } else {
                        return Completable.error(error);
                    }
                });
    }
}
