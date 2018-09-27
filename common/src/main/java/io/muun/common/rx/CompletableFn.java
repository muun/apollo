package io.muun.common.rx;

import io.muun.common.Optional;
import io.muun.common.api.error.ErrorCode;
import io.muun.common.exception.HttpException;

import rx.Completable;
import rx.functions.Func1;

/**
 * Utility transformers for modifying singles.
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
                new Func1<ErrorT, Completable>() {

                    @Override
                    public Completable call(ErrorT error) {
                        return Completable.error(replacer.call(error));
                    }

                }
        );
    }

    /**
     * Consume and ignore errors of a given type.
     */
    public static <ErrorT extends Throwable> Completable.Transformer ignoreTypedError(
            final Class<ErrorT> errorClass) {

        return onTypedErrorResumeNext(
                errorClass,
                new Func1<ErrorT, Completable>() {

                    @Override
                    public Completable call(ErrorT error) {
                        return Completable.complete();
                    }

                }
        );
    }

    /**
     * Like onErrorResumeNext, but only for errors of a specific type.
     */
    public static <ErrorT extends Throwable> Completable.Transformer onTypedErrorResumeNext(
            final Class<ErrorT> errorClass,
            final Func1<ErrorT, Completable> resumeFunction) {

        return new Completable.Transformer() {

            @Override
            public Completable call(Completable completable) {

                return completable.onErrorResumeNext(

                        new Func1<Throwable, Completable>() {

                            @Override
                            public Completable call(Throwable error) {

                                final Optional<ErrorT> cause = getTypedCause(error, errorClass);

                                if (cause.isPresent()) {
                                    return resumeFunction.call(errorClass.cast(cause.get()));
                                } else {
                                    return Completable.error(error);
                                }

                            }
                        }

                );

            }
        };
    }

    /**
     * Consume and ignore HttpExceptions of a given type.
     */
    public static Completable.Transformer ignoreHttpException(final ErrorCode code) {

        return onHttpExceptionResumeNext(
                code,
                new Func1<HttpException, Completable>() {

                    @Override
                    public Completable call(HttpException error) {
                        return Completable.complete();
                    }

                }
        );
    }

    /**
     * Like onTypedErrorResumeNext, but specialized to HttpExceptions.
     */
    public static Completable.Transformer onHttpExceptionResumeNext(
            final ErrorCode code,
            final Func1<HttpException, Completable> resumeFunction) {

        return new Completable.Transformer() {

            @Override
            public Completable call(Completable completable) {

                return completable.compose(onTypedErrorResumeNext(
                        HttpException.class,
                        new Func1<HttpException, Completable>() {

                            @Override
                            public Completable call(HttpException error) {

                                if (error.getErrorCode().equals(code)) {
                                    return resumeFunction.call(error);
                                }

                                return Completable.error(error);
                            }
                        }
                ));
            }
        };
    }

    private static <T extends Throwable> Optional<T> getTypedCause(
            Throwable error,
            Class<T> errorClass) {

        for (Throwable cause = error;  cause != null; cause = cause.getCause()) {
            if (errorClass.isInstance(cause)) {
                return Optional.of(errorClass.cast(cause));
            }
        }

        return Optional.empty();
    }
}
